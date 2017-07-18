package org.kaivos.röda;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.kaivos.röda.Parser.parse;
import static org.kaivos.röda.Parser.parseStatement;
import static org.kaivos.röda.Parser.t;
import static org.kaivos.röda.RödaValue.BOOLEAN;
import static org.kaivos.röda.RödaValue.FLOATING;
import static org.kaivos.röda.RödaValue.FUNCTION;
import static org.kaivos.röda.RödaValue.INTEGER;
import static org.kaivos.röda.RödaValue.LIST;
import static org.kaivos.röda.RödaValue.MAP;
import static org.kaivos.röda.RödaValue.NAMESPACE;
import static org.kaivos.röda.RödaValue.NFUNCTION;
import static org.kaivos.röda.RödaValue.NUMBER;
import static org.kaivos.röda.RödaValue.REFERENCE;
import static org.kaivos.röda.RödaValue.STRING;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.kaivos.nept.parser.ParsingException;
import org.kaivos.nept.parser.TokenList;
import org.kaivos.röda.Parser.AnnotationTree;
import org.kaivos.röda.Parser.ArgumentTree;
import org.kaivos.röda.Parser.Command;
import org.kaivos.röda.Parser.DatatypeTree;
import org.kaivos.röda.Parser.ExpressionTree;
import org.kaivos.röda.Parser.ExpressionTree.CType;
import org.kaivos.röda.Parser.FunctionTree;
import org.kaivos.röda.Parser.KwArgumentTree;
import org.kaivos.röda.Parser.ParameterTree;
import org.kaivos.röda.Parser.ProgramTree;
import org.kaivos.röda.Parser.RecordTree;
import org.kaivos.röda.Parser.StatementTree;
import org.kaivos.röda.runtime.Datatype;
import org.kaivos.röda.runtime.Function;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.runtime.Record;
import org.kaivos.röda.type.RödaBoolean;
import org.kaivos.röda.type.RödaFloating;
import org.kaivos.röda.type.RödaFunction;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaList;
import org.kaivos.röda.type.RödaMap;
import org.kaivos.röda.type.RödaNamespace;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaRecordInstance;
import org.kaivos.röda.type.RödaReference;
import org.kaivos.röda.type.RödaString;

public class Interpreter {

	/*** INTERPRETER ***/

	public static class RödaScope {
		Optional<RödaScope> parent;
		Map<String, RödaValue> map;
		Map<String, Datatype> typeargs;
		Map<String, RecordDeclaration> records = new HashMap<>();
		public RödaScope(Optional<RödaScope> parent) {
			this.parent = parent;
			this.map = new HashMap<>();
			this.typeargs = new HashMap<>();
		}
		public RödaScope(RödaScope parent) {
			this(Optional.of(parent));
		}

		public synchronized RödaValue resolve(String name) {
			if (map.containsKey(name)) return map.get(name);
			if (parent.isPresent()) return parent.get().resolve(name);
			return null;
		}

		public synchronized void set(String name, RödaValue value) {
			if (map.containsKey(name))
				map.put(name, value);
			else if (parent.isPresent() && parent.get().resolve(name) != null)
				parent.get().set(name, value);
			else {
				map.put(name, value);
			}
		}

		public synchronized void setLocal(String name, RödaValue value) {
			map.put(name, value);
		}
		
		public Set<String> getLocalVariableNames() {
			return Collections.unmodifiableSet(map.keySet());
		}

		public void addTypearg(String name, Datatype value) {
			if (getTypearg(name) != null) {
				error("can't override typeargument '" + name + "'");
			}
			typeargs.put(name, value);
		}

		public Datatype getTypearg(String name) {
			if (typeargs.containsKey(name)) {
				return typeargs.get(name);
			}
			if (parent.isPresent()) {
				return parent.get().getTypearg(name);
			}
			return null;
		}

		public Datatype substitute(DatatypeTree type) {
			if (type.name.size() == 1) {
				Datatype typearg = getTypearg(type.name.get(0));
				if (typearg != null) {
					if (!type.subtypes.isEmpty())
						error("a typeparameter can't have subtypes");
					return typearg;
				}
			}
			List<Datatype> subtypes = new ArrayList<>();
			for (DatatypeTree t : type.subtypes) {
				subtypes.add(substitute(t));
			}
			RödaScope scope = this;
			for (int i = 0; i < type.name.size()-1; i++) {
				String namespace = type.name.get(i);
				RödaValue value = scope.resolve(namespace);
				if (value == null || !value.is(NAMESPACE))
					typeMismatch("invalid datatype (namespace not found): " + type.toString());
				scope = value.scope();
			}
			return new Datatype(type.name.get(type.name.size()-1), subtypes, scope);
		}
		
		public Datatype substitute(Datatype type) {
			Datatype typearg = getTypearg(type.name);
			if (typearg != null) {
				if (!type.subtypes.isEmpty())
					error("a typeparameter can't have subtypes");
				return typearg;
			}
			List<Datatype> subtypes = new ArrayList<>();
			for (Datatype t : type.subtypes) {
				subtypes.add(substitute(t));
			}
			return new Datatype(type.name, subtypes, type.scope);
		}
		
		public Map<String, Record> getRecords() {
			Map<String, Record> records = new HashMap<>();
			if (parent.isPresent()) {
				records.putAll(parent.get().getRecords());
			}
			this.records.values().forEach(r -> records.put(r.tree.name, r.tree));
			return Collections.unmodifiableMap(records);
		}
		
		public Map<String, RecordDeclaration> getRecordDeclarations() {
			Map<String, RecordDeclaration> records = new HashMap<>();
			if (parent.isPresent()) {
				records.putAll(parent.get().getRecordDeclarations());
			}
			records.putAll(this.records);
			return Collections.unmodifiableMap(records);
		}

		public void preRegisterRecord(Record record) {
			records.put(record.name, new RecordDeclaration(record, INTERPRETER.createRecordClassReflection(record, this)));
		}

		public void postRegisterRecord(Record record) {
			INTERPRETER.createFieldReflections(record, records.get(record.name).reflection, this);
		}
		
		public void registerRecord(RecordDeclaration record) {
			records.put(record.tree.name, record);
		}
	}
	
	public static class RecordDeclaration {
		public final Record tree;
		public final RödaValue reflection;
		
		public RecordDeclaration(Record record, RödaValue reflection) {
			this.tree = record;
			this.reflection = reflection;
		}
	}
	
	private static Record treeToRecord(RecordTree recordTree, RödaScope scope) {
		return new Record(recordTree.name, recordTree.typeparams,
				recordTree.params,
				recordTree.superTypes.stream().map(st -> new Record.SuperExpression(scope.substitute(st.type), st.args)).collect(toList()),
				recordTree.annotations,
				recordTree.fields.stream().map(f -> new Record.Field(f.name, scope.substitute(f.type), f.defaultValue, f.annotations)).collect(toList()),
				recordTree.isValueType, scope);
	}
	
	private static Function treeToFunction(FunctionTree funcTree, RödaScope scope) {
		return new Function(funcTree.name, funcTree.typeparams,
				funcTree.parameters.stream().map(p -> treeToParameter(p, scope)).collect(toList()),
				funcTree.isVarargs,
				funcTree.kwparameters.stream().map(p -> treeToParameter(p, scope)).collect(toList()),
				funcTree.body);
	}
	
	private static Parameter treeToParameter(ParameterTree parTree, RödaScope scope) {
		return new Parameter(parTree.name, parTree.reference,
				parTree.type == null ? null : scope.substitute(parTree.type), parTree.defaultValue);
	}

	public RödaScope G = new RödaScope(Optional.empty());

	public File currentDir = new File(System.getProperty("user.dir"));
	
	private Record errorSubtype(String name) {
		return new Record(name,
				emptyList(),
				Arrays.asList(new Record.SuperExpression(new Datatype("Error", G), emptyList())),
				emptyList(),
				false, G);
	}
	
	private Record errorSubtype(String name, String... superTypes) {
		return new Record(name,
				emptyList(),
				Arrays.asList(superTypes).stream()
					.map(t -> new Record.SuperExpression(new Datatype(t, G), emptyList()))
					.collect(toList()),
				emptyList(),
				false, G);
	}

	private final Record errorRecord, typeRecord, fieldRecord;
	private final Record
		javaErrorRecord,
		leakyPipeErrorRecord,
		streamErrorRecord,
		emptyStreamErrorRecord,
		fullStreamErrorRecord,
		illegalArgumentsErrorRecord,
		unknownNameErrorRecord,
		typeMismatchErrorRecord,
		outOfBoundsErrorRecord;
	{
		errorRecord = new Record("Error",
				emptyList(),
				emptyList(),
				Arrays.asList(new Record.Field("message", new Datatype("string")),
						new Record.Field("stack",
								new Datatype("list", Arrays.asList(new Datatype("string")))),
						new Record.Field("javastack",
								new Datatype("list", Arrays.asList(new Datatype("string")))),
						new Record.Field("causes",
								new Datatype("list", Arrays.asList(new Datatype("Error", G))))
						),
				false, G);
		typeRecord = new Record("Type",
				emptyList(),
				emptyList(),
				Arrays.asList(new Record.Field("name", new Datatype("string")),
						new Record.Field("annotations", new Datatype("list")),
						new Record.Field("fields",
								new Datatype("list", Arrays.asList(new Datatype("Field", G)))),
						new Record.Field("newInstance", new Datatype("function"))
						),
				false, G);
		fieldRecord = new Record("Field",
				emptyList(),
				emptyList(),
				Arrays.asList(new Record.Field("name", new Datatype("string")),
						new Record.Field("annotations", new Datatype("list")),
						new Record.Field("type", new Datatype("Type", G)),
						new Record.Field("get", new Datatype("function")),
						new Record.Field("set", new Datatype("function"))
						),
				false, G);
		
		javaErrorRecord = errorSubtype("JavaError");
		leakyPipeErrorRecord = errorSubtype("LeakyPipeError");
		streamErrorRecord = errorSubtype("StreamError");
		emptyStreamErrorRecord = errorSubtype("EmptyStreamError", "StreamError");
		fullStreamErrorRecord = errorSubtype("FullStreamError", "StreamError");
		illegalArgumentsErrorRecord = errorSubtype("IllegalArgumentsError");
		unknownNameErrorRecord = errorSubtype("UnknownNameError");
		typeMismatchErrorRecord = errorSubtype("TypeMismatchError", "IllegalArgumentsError");
		outOfBoundsErrorRecord = errorSubtype("OutOfBoundsError", "IllegalArgumentsError");
	}
	private final Record[] builtinRecords = {
			typeRecord, errorRecord, fieldRecord,
			leakyPipeErrorRecord,
			javaErrorRecord,
			streamErrorRecord,
			emptyStreamErrorRecord,
			fullStreamErrorRecord,
			illegalArgumentsErrorRecord,
			unknownNameErrorRecord,
			typeMismatchErrorRecord,
			outOfBoundsErrorRecord
	};

	public void populateBuiltins() {
		if (enableProfiling) pushTimer();
		for (Record r : builtinRecords) {
			G.preRegisterRecord(r);
		}
		for (Record r : builtinRecords) {
			G.postRegisterRecord(r);
		}
		
		Builtins.populate(this);

		G.setLocal("ENV", RödaMap.of(System.getenv().entrySet().stream()
				.collect(toMap(e -> e.getKey(),
						e -> RödaString.of(e.getValue())))));
		if (enableProfiling) popTimer("<populate builtins>");
	}

	private RödaValue createRecordClassReflection(Record record, RödaScope scope) {
		if (enableProfiling) pushTimer();
		if (enableDebug) callStack.get().push("creating reflection object of record "
				+ record.name + "\n\tat <runtime>");
		RödaValue typeObj = RödaRecordInstance.of(typeRecord, emptyList());
		typeObj.setField("name", RödaString.of(record.name));
		typeObj.setField("annotations", evalAnnotations(record.annotations, scope));
		typeObj.setField("newInstance", RödaNativeFunction
				.of("Type.newInstance",
						(ta, a, k, s, i, o) -> {
							o.push(newRecord(new Datatype(record.name, emptyList(), scope), ta, a, scope));
						}, emptyList(), false));
		if (enableDebug) callStack.get().pop();
		if (enableProfiling) popTimer("<create record class reflection>");
		return typeObj;
	}

	private void createFieldReflections(Record record, RödaValue typeObj, RödaScope scope) {
		if (enableDebug) callStack.get().push("creating field reflection objects of record "
				+ record.name + "\n\tat <runtime>");
		typeObj.setField("fields", RödaList.of(new Datatype("Field", G), record.fields.stream()
				.map(f -> createFieldReflection(record, f, scope))
				.collect(toList())));
		if (enableDebug) callStack.get().pop();
	}

	private RödaValue createFieldReflection(Record record, Record.Field field, RödaScope scope) {
		if (enableProfiling) pushTimer();
		if (enableDebug) callStack.get().push("creating reflection object of field "
				+ record.name + "." + field.name + "\n\tat <runtime>");
		RödaValue fieldObj = RödaRecordInstance.of(fieldRecord, emptyList());
		fieldObj.setField("name", RödaString.of(field.name));
		fieldObj.setField("annotations", evalAnnotations(field.annotations, scope));
		fieldObj.setField("get", RödaNativeFunction
				.of("Field.get",
						(ta, a, k, s, i, o) -> {
							RödaValue obj = a.get(0);
							if (!obj.is(new Datatype(record.name, emptyList(), scope))) {
								illegalArguments("illegal argument for Field.get: "
										+ record.name + " required, got " + obj.typeString());
							}
							o.push(obj.getField(field.name));
						}, Arrays.asList(new Parameter("object", false)), false));
		fieldObj.setField("set", RödaNativeFunction
				.of("Field.set",
						(ta, a, k, s, i, o) -> {
							RödaValue obj = a.get(0);
							if (!obj.is(new Datatype(record.name, emptyList(), scope))) {
								illegalArguments("illegal argument for Field.get: "
										+ record.name + " required, got " + obj.typeString());
							}
							RödaValue val = a.get(1);
							obj.setField(field.name, val);
						}, Arrays.asList(new Parameter("object", false),
								new Parameter("value", false)), false));
		if (scope.getRecordDeclarations().containsKey(field.type.name))
			fieldObj.setField("type", scope.getRecordDeclarations().get(field.type.name).reflection);
		if (enableDebug) callStack.get().pop();
		if (enableProfiling) popTimer("<create field reflection>");
		return fieldObj;
	}

	private RödaValue evalAnnotations(List<AnnotationTree> annotations, RödaScope scope) {
		return annotations.stream()
				.map(a -> {
					RödaScope annotationScope = scope;
					for (String var : a.namespace) {
						RödaValue val = annotationScope.resolve(var);
						if (val == null)
							unknownName("namespace '" + var + "' not found");
						if (!val.is(NAMESPACE)) {
							typeMismatch("type mismatch: expected namespace, got " + val.typeString());
						}
						annotationScope = val.impliciteResolve().scope();
					}
					RödaValue function = annotationScope.resolve(a.name);
					if (function == null) unknownName("annotation function '" + a.name + "' not found");
					function = function.impliciteResolve();
					List<RödaValue> args = flattenArguments(a.args.arguments, scope,
							RödaStream.makeEmptyStream(),
							RödaStream.makeStream(),
							false);
					Map<String, RödaValue> kwargs = kwargsToMap(a.args.kwarguments, scope,
							RödaStream.makeEmptyStream(),
							RödaStream.makeStream(), false);
					RödaStream _out = RödaStream.makeStream();
					exec(a.file, a.line, function, emptyList(), args, kwargs,
							G, RödaStream.makeEmptyStream(), _out);
					_out.finish();
					RödaValue list = _out.readAll();
					return list.list();
				})
				.collect(() -> RödaList.empty(),
						(list, values) -> { list.addAll(values); },
						(list, list2) -> { list.addAll(list2.list()); });
	}

	public static ExecutorService executor = Executors.newCachedThreadPool();

	public static void shutdown() {
		executor.shutdown();
	}

	public static final Interpreter INTERPRETER = new Interpreter();
	
	private Interpreter() {}

	/* kutsupino */
	
	public static ThreadLocal<ArrayDeque<String>> callStack = new InheritableThreadLocal<ArrayDeque<String>>() {
		@Override protected ArrayDeque<String> childValue(ArrayDeque<String> parentValue) {
			return new ArrayDeque<>(parentValue);
		}
	};

	static { callStack.set(new ArrayDeque<>()); }
	
	/* profiloija */
	
	public static class ProfilerData {
		public final String function;
		public int invocations = 0;
		public long time = 0;
		
		public ProfilerData(String function) {
			this.function = function;
		}
	}
	
	public static Map<String, ProfilerData> profilerData = new HashMap<>();
	
	private synchronized void updateProfilerData(String function, long value) {
		ProfilerData data;
		if (!profilerData.containsKey(function)) profilerData.put(function, data = new ProfilerData(function));
		else data = profilerData.get(function);
		data.time += value;
		data.invocations++;
	}
	
	private static ThreadLocal<ArrayDeque<Timer>> timerStack = ThreadLocal.withInitial(ArrayDeque::new);
	
	void pushTimer() {
		ArrayDeque<Timer> ts = timerStack.get();
		if (!ts.isEmpty()) {
			ts.peek().stop();
		}
		Timer t = new Timer();
		ts.push(t);
		t.start();
	}
	
	void popTimer(String name) {
		ArrayDeque<Timer> ts = timerStack.get();
		Timer t = ts.pop();
		t.stop();
		updateProfilerData(name, t.timeNanos());
		if (!ts.isEmpty()) {
			ts.peek().start();
		}
	}
	
	public boolean enableDebug = true, enableProfiling = false;

	@SuppressWarnings("serial")
	public static class RödaException extends RuntimeException {
		private Throwable[] causes;
		private Deque<String> stack;
		private RödaValue errorObject;
		private RödaException(String message, Deque<String> stack, RödaValue errorObject) {
			super(message);
			this.causes = new Throwable[0];
			this.stack = stack;
			this.errorObject = errorObject;
		}

		private RödaException(Throwable cause, Deque<String> stack, RödaValue errorObject) {
			super(cause);
			this.stack = stack;
			this.errorObject = errorObject;
		}

		private RödaException(Throwable[] causes, Deque<String> stack, RödaValue errorObject) {
			super(causes.length == 1 ? causes[0].getClass().getName() + ": " + causes[0].getMessage()
					: "multiple threads crashed", causes[0]);
			this.causes = causes;
			this.stack = stack;
			this.errorObject = errorObject;
		}
		
		public Throwable[] getCauses() {
			return causes;
		}

		public Deque<String> getStack() {
			return stack;
		}

		public RödaValue getErrorObject() {
			return errorObject;
		}
	}

	private static RödaValue makeErrorObject(Record record, String message,
			StackTraceElement[] javaStackTrace, Throwable... causes) {
		RödaValue errorObject = RödaRecordInstance.of(record, emptyList());
		errorObject.setField("message", RödaString.of(message));
		errorObject.setField("stack", RödaList.of("string", callStack.get().stream()
				.map(RödaString::of).collect(toList())));
		errorObject.setField("javastack", RödaList.of("string", Arrays.stream(javaStackTrace)
				.map(StackTraceElement::toString).map(RödaString::of)
				.collect(toList())));
		errorObject.setField("causes", RödaList.of(new Datatype("Error", INTERPRETER.G), Arrays.stream(causes)
				.map(cause -> cause instanceof RödaException ? ((RödaException) cause).getErrorObject()
						: makeErrorObject(INTERPRETER.javaErrorRecord,
								cause.getClass().getName() + ": " + cause.getMessage(), cause.getStackTrace()))
				.collect(toList())));
		return errorObject;
	}

	private static RödaException createRödaException(Record record, String message) {
		RödaValue errorObject = makeErrorObject(record, message, Thread.currentThread().getStackTrace());
		return new RödaException(message, new ArrayDeque<>(callStack.get()), errorObject);
	}
	
	public static void error(String message) {
		throw createRödaException(INTERPRETER.errorRecord, message);
	}
	
	public static void streamError(String message) {
		throw createRödaException(INTERPRETER.streamErrorRecord, message);
	}
	
	public static void emptyStream(String message) {
		throw createRödaException(INTERPRETER.emptyStreamErrorRecord, message);
	}
	
	public static void fullStream(String message) {
		throw createRödaException(INTERPRETER.fullStreamErrorRecord, message);
	}
	
	public static void illegalArguments(String message) {
		throw createRödaException(INTERPRETER.illegalArgumentsErrorRecord, message);
	}
	
	public static void unknownName(String message) {
		throw createRödaException(INTERPRETER.unknownNameErrorRecord, message);
	}
	
	public static void typeMismatch(String message) {
		throw createRödaException(INTERPRETER.typeMismatchErrorRecord, message);
	}
	
	public static void outOfBounds(String message) {
		throw createRödaException(INTERPRETER.outOfBoundsErrorRecord, message);
	}
	
	private static RödaException createRödaException(Throwable...causes) {
		if (causes.length == 1 && causes[0] instanceof RödaException) return (RödaException) causes[0];
		
		String message;
		if (causes.length == 1) message = causes[0].getClass().getName() + ": " + causes[0].getMessage();
		else message = "multiple threads crashed";
		
		StackTraceElement[] javaStackTrace;
		if (causes.length == 1) javaStackTrace = causes[0].getStackTrace();
		else javaStackTrace = Thread.currentThread().getStackTrace();
		
		RödaValue errorObject = makeErrorObject(causes.length == 1 ? INTERPRETER.javaErrorRecord : INTERPRETER.errorRecord,
				message, javaStackTrace, causes);
		return new RödaException(causes, new ArrayDeque<>(callStack.get()), errorObject);
	}

	public static void error(Throwable... causes) {
		throw createRödaException(causes);
	}

	public static void error(RödaValue errorObject) {
		RödaException e = new RödaException(errorObject.getField("message").str(), new ArrayDeque<>(callStack.get()), errorObject);
		throw e;
	}

	@SuppressWarnings("unused")
	private static void printStackTrace() {
		for (String step : callStack.get()) {
			System.err.println(step);
		}
	}

	public void interpret(String code, RödaStream in, RödaStream out) {
		interpret(code, "<input>", in, out);
	}

	public void interpret(String code, String filename, RödaStream in, RödaStream out) {
		interpret(code, new ArrayList<>(), filename, in, out);
	}

	public void interpret(String code, List<RödaValue> args, String filename, RödaStream in, RödaStream out) {
		try {
			load(code, filename, G);

			RödaValue main = G.resolve("main");
			if (main == null) return;
			if (!main.is(FUNCTION) || main.is(NFUNCTION))
				typeMismatch("The variable 'main' must be a function");

			exec("<runtime>", 0, main, emptyList(), args, Collections.emptyMap(), G, in, out);
		} catch (ParsingException|RödaException e) {
			throw e;
		} catch (Exception e) {
			error(e);
		}
	}

	public void load(String code, String filename, RödaScope scope, boolean overwrite) {
		try {
			if (enableProfiling) pushTimer();
			ProgramTree program = parse(t.tokenize(code, filename));
			if (enableProfiling) popTimer("<parser>");
			for (List<StatementTree> f : program.preBlocks) {
				execBlock("pre_load", f, scope);
			}
			for (FunctionTree f : program.functions) {
				scope.setLocal(f.name, RödaFunction.of(treeToFunction(f, scope), scope));
			}
			for (RecordTree r : program.records) {
				scope.preRegisterRecord(treeToRecord(r, scope));
			}
			for (RecordTree r : program.records) {
				scope.postRegisterRecord(treeToRecord(r, scope));
			}
			for (List<StatementTree> f : program.postBlocks) {
				execBlock("post_load", f, scope);
			}
		} catch (ParsingException|RödaException e) {
			throw e;
		} catch (Exception e) {
			error(e);
		}
	}
	
	private void execBlock(String name, List<StatementTree> block, RödaScope scope) {
		if (enableProfiling) pushTimer();
		if (enableDebug) callStack.get().push("calling block " + name + "\n\tat <load>");
		try {
			RödaStream in = RödaStream.makeEmptyStream();
			RödaStream out = RödaStream.makeEmptyStream();
			for (StatementTree s : block) {
				evalStatement(s, scope, in, out, false);
			}
		}
		finally {
			if (enableDebug) callStack.get().pop();
			if (enableProfiling) popTimer(name);
		}
	}
	
	public void load(String code, String filename, RödaScope scope) {
		load(code, filename, scope, true);
	}

	public void loadFile(File file, RödaScope scope, boolean overwrite) {
		try {
			BufferedReader in = new BufferedReader(
					new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
			String code = "";
			String line = "";
			while ((line = in.readLine()) != null) {
				code += line + "\n";
			}
			in.close();
			load(code, file.getName(), scope, overwrite);
		} catch (IOException e) {
			error(e);
		}
	}
	
	public void loadFile(File file, RödaScope scope) {
		loadFile(file, scope, true);
	}

	public void interpretStatement(String code, String filename, RödaStream in, RödaStream out) {
		try {
			TokenList tl = t.tokenize(code, filename);
			StatementTree statement = parseStatement(tl);
			tl.accept("<EOF>");
			evalStatement(statement, G, in, out, false);
		} catch (RödaException e) {
			throw e;
		} catch (ParsingException e) {
			throw e;
		} catch (Exception e) {
			error(e);
		} 
	}

	private Parameter getParameter(RödaValue function, int i) {
		assert function.is(FUNCTION);
		boolean isNative = function.is(NFUNCTION);
		List<Parameter> parameters = isNative ? function.nfunction().parameters
				: function.function().parameters;
		boolean isVarargs = isNative ? function.nfunction().isVarargs : function.function().isVarargs;
		if (isVarargs && i >= parameters.size()-1) return parameters.get(parameters.size()-1);
		else if (i >= parameters.size()) return null;
		else return parameters.get(i);
	}

	private boolean isReferenceParameter(RödaValue function, int i) {
		Parameter p = getParameter(function, i);
		if (p == null) return false; // tästä tulee virhe myöhemmin
		return p.reference;
	}

	private List<Parameter> getKwParameters(RödaValue function) {
		if (function.is(NFUNCTION)) {
			return function.nfunction().kwparameters;
		}
		if (function.is(FUNCTION)) {
			return function.function().kwparameters;
		}
		return emptyList();
	}

	public static void checkReference(String function, RödaValue arg) {
		if (!arg.is(REFERENCE)) {
			typeMismatch("illegal argument for '" + function
					+ "': reference expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkList(String function, RödaValue arg) {
		if (!arg.is(LIST)) {
			typeMismatch("illegal argument for '" + function
					+ "': list expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkMap(String function, RödaValue arg) {
		if (!arg.is(MAP)) {
			typeMismatch("illegal argument for '" + function
					+ "': list expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkListOrString(String function, RödaValue arg) {
		if (!arg.is(LIST) && !arg.is(STRING)) {
			typeMismatch("illegal argument for '" + function
					+ "': list or string expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkListOrNumber(String function, RödaValue arg) {
		if (!arg.is(LIST) && !arg.is(NUMBER)) {
			typeMismatch("illegal argument for '" + function
					+ "': list or number expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkString(String function, RödaValue arg) {
		if (!arg.is(STRING)) {
			typeMismatch("illegal argument for '" + function
					+ "': string expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkNumber(String function, RödaValue arg) {
		if (!arg.is(NUMBER)) {
			typeMismatch("illegal argument for '" + function
					+ "': number expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkInteger(String function, RödaValue arg) {
		if (!arg.is(INTEGER)) {
			typeMismatch("illegal argument for '" + function
					+ "': integer expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkBoolean(String function, RödaValue arg) {
		if (!arg.is(BOOLEAN)) {
			typeMismatch("illegal argument for '" + function
					+ "': boolean expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkFunction(String function, RödaValue arg) {
		if (!arg.is(FUNCTION)) {
			typeMismatch("illegal argument for '" + function
					+ "': function expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkArgs(String function, int required, int got) {
		if (got > required) argumentOverflow(function, required, got);
		if (got < required) argumentUnderflow(function, required, got);
	}

	private static void checkArgs(String name, boolean isVarargs,
			List<Parameter> parameters,
			List<Parameter> kwparameters,
			List<RödaValue> args, Map<String, RödaValue> kwargs,
			RödaScope scope) {
		if (!isVarargs) {
			checkArgs(name, parameters.size(), args.size());
		} else {
			if (args.size() < parameters.size()-1)
				argumentUnderflow(name, parameters.size()-1, args.size());
		}

		for (int i = 0; i < Math.min(args.size(), parameters.size()); i++) {
			if (parameters.get(i).type == null) continue;
			Datatype t = scope.substitute(parameters.get(i).type);
			if (!args.get(i).is(t))
				typeMismatch("illegal argument '" + parameters.get(i).name + "' for '"+name+"': "
						+ t + " expected (got "
						+ args.get(i).typeString() + ")");
		}

		for (Parameter par : kwparameters) {
			if (!kwargs.containsKey(par.name))
				illegalArguments("illegal arguments for '" + name + "': kw argument " + par.name + " not found");
			if (par.type == null) continue;
			Datatype t = scope.substitute(par.type);
			if (!kwargs.get(par.name).is(t))
				typeMismatch("illegal argument '" + par.name + "' for '"+name+"': "
						+ t + " expected (got "
						+ kwargs.get(par.name).typeString() + ")");
		}
	}

	public static void argumentOverflow(String function, int required, int got) {
		illegalArguments("illegal number of arguments for '" + function
				+ "': at most " + required + " required (got " + got + ")");
	}

	public static void argumentUnderflow(String function, int required, int got) {
		illegalArguments("illegal number of arguments for '" + function
				+ "': at least " + required + " required (got " + got + ")");
	}
	
	private RödaValue resolveArgument(RödaValue val, boolean isReferenceParameter) {
		// jos kyseessä ei ole viittausparametri, resolvoidaan viittaus
		if (val.is(REFERENCE) && !isReferenceParameter) {
			RödaValue rval = val.resolve(true);
			if (rval.is(REFERENCE)) rval = rval.resolve(true); // tuplaviittaus
			return rval;
		}
		// jos kyseessä on viittausparametri, resolvoidaan viittaus vain, jos kyseessä on tuplaviittaus
		if (val.is(REFERENCE) && isReferenceParameter) {
			RödaValue rval = val.unsafeResolve();
			if (rval != null && rval.is(REFERENCE)) return rval; // tuplaviittaus
			else return val;
		}
		return val;
	}

	public void exec(String file, int line,
			RödaValue value, List<Datatype> typeargs,
			List<RödaValue> rawArgs, Map<String, RödaValue> rawKwArgs,
			RödaScope scope, RödaStream in, RödaStream out) {
		List<RödaValue> args = new ArrayList<>();
		Map<String, RödaValue> kwargs = new HashMap<>();
		int i = 0;
		for (RödaValue val : rawArgs) {
			val = resolveArgument(val, value.is(FUNCTION) && isReferenceParameter(value, i));
			args.add(val);
			i++;
		}

		for (Parameter kwpar : getKwParameters(value)) {
			if (!rawKwArgs.containsKey(kwpar.name)) {
				RödaValue defaultVal = evalExpression(kwpar.defaultValue, G,
						RödaStream.makeEmptyStream(),
						RödaStream.makeStream()).impliciteResolve();
				kwargs.put(kwpar.name, defaultVal);
				continue;
			}
			RödaValue val = rawKwArgs.get(kwpar.name);
			val = resolveArgument(val, false);
			kwargs.put(kwpar.name, val);
		}
		
		if (value.is(NFUNCTION) && value.nfunction().isKwVarargs) {
			for (Entry<String, RödaValue> arg : rawKwArgs.entrySet()) {
				if (!kwargs.containsKey(arg.getKey())) {
					kwargs.put(arg.getKey(), resolveArgument(arg.getValue(), false));
				}
			}
		}

		if (enableProfiling) {
			pushTimer();
		}
		if (enableDebug) {
			if (args.size() > 0) {
				callStack.get().push("calling " + value.str()
					+ " with argument" + (args.size() == 1 ? " " : "s ")
					+ args.stream()
						.map(RödaValue::str)
						.collect(joining(", "))
					+ "\n\tat " + file + ":" + line);
			}
			else {
				callStack.get().push("calling " + value.str()
					+ " with no arguments\n"
					+ "\tat " + file + ":" + line);
			}
		}
		try {
			execWithoutErrorHandling(value, typeargs, args, kwargs, scope, in, out);
		}
		catch (RödaException e) { throw e; }
		catch (Throwable e) { error(e); }
		finally {
			if (enableDebug) {
				callStack.get().pop();
			}
			if (enableProfiling) {
				popTimer(value.str());
			}
		}
	}

	@SuppressWarnings("serial")
	private static class ReturnException extends RuntimeException { }
	private static final ReturnException RETURN_EXCEPTION = new ReturnException();

	public void execWithoutErrorHandling(
			RödaValue value,
			List<Datatype> typeargs,
			List<RödaValue> args, Map<String, RödaValue> kwargs,
			RödaScope scope, RödaStream in, RödaStream out) {

		//System.err.println("exec " + value + "("+args+") " + in + " -> " + out);
		if (value.is(LIST)) {
			checkArgs("[]", 0, args.size());
			if (!kwargs.isEmpty()) argumentOverflow("[]", 0, kwargs.size()); // TODO parempi virheviesti
			for (RödaValue item : value.list())
				out.push(item);
			return;
		}
		if (value.is(FUNCTION) && !value.is(NFUNCTION)) {
			boolean isVarargs = value.function().isVarargs;
			List<String> typeparams = value.function().typeparams;
			List<Parameter> parameters = value.function().parameters;
			List<Parameter> kwparameters = value.function().kwparameters;
			String name = value.function().name;

			// joko nimettömän funktion paikallinen scope tai ylätason scope
			RödaScope newScope = value.localScope() == null
					? new RödaScope(G) : new RödaScope(value.localScope());
			
			newScope.setLocal("caller_namespace", RödaNamespace.of(scope));
			
			if (typeparams.size() != typeargs.size())
				illegalArguments("illegal number of typearguments for '" + name + "': "
						+ typeparams.size() + " required, got " + typeargs.size());
			for (int i = 0; i < typeparams.size(); i++) {
				newScope.addTypearg(typeparams.get(i), typeargs.get(i));
			}

			checkArgs(name, isVarargs, parameters, kwparameters, args, kwargs, newScope);

			int j = 0;
			for (Parameter p : parameters) {
				if (isVarargs && j == parameters.size()-1) break;
				newScope.setLocal(p.name, args.get(j++));
			}
			if (isVarargs) {
				RödaValue argslist = RödaList.of(new ArrayList<>());
				if (args.size() >= parameters.size()) {
					for (int k = parameters.size()-1; k < args.size(); k++) {
						argslist.add(args.get(k));
					}
				}
				newScope.setLocal(parameters.get(parameters.size()-1).name, argslist);
			}
			for (Parameter p : kwparameters) {
				newScope.setLocal(p.name, kwargs.get(p.name));
			}
			for (StatementTree s : value.function().body) {
				try {
					evalStatement(s, newScope, in, out, false);
				} catch (ReturnException e) {
					break;
				}
			}
			return;
		}
		if (value.is(NFUNCTION)) {
			checkArgs(value.nfunction().name, value.nfunction().isVarargs,
					value.nfunction().parameters, value.nfunction().kwparameters,
					args, kwargs, scope);
			value.nfunction().body.exec(typeargs, args, kwargs, scope, in, out);
			return;
		}
		typeMismatch("can't execute a value of type " + value.typeString());
	}
	
	public boolean singleThreadMode = false;
	
	private void evalStatement(StatementTree statement, RödaScope scope,
			RödaStream in, RödaStream out, boolean redirected) {
		if (singleThreadMode) {
			evalStatementST(statement, scope, in, out, redirected);
		}
		else {
			evalStatementCC(statement, scope, in, out, redirected);
		}
	}
	
	private void evalStatementST(StatementTree statement, RödaScope scope,
			RödaStream in, RödaStream out, boolean redirected) {
		RödaStream _in = in;
		int i = 0;
		for (Command command : statement.commands) {
			boolean last = i == statement.commands.size()-1;
			RödaStream _out = last ? out : RödaStream.makeStream();
			evalCommand(command, scope,
					in, out,
					_in, _out).run();
			if (!last || redirected)
				_out.finish();
			_in = _out;
			i++;
		}
	}

	private void evalStatementCC(StatementTree statement, RödaScope scope,
			RödaStream in, RödaStream out, boolean redirected) {
		RödaStream _in = in;
		int i = 0;
		Runnable[] runnables = new Runnable[statement.commands.size()];
		Timer[] timers = new Timer[statement.commands.size()];
		for (Command command : statement.commands) {
			boolean last = i == statement.commands.size()-1;
			RödaStream _out = last ? out : RödaStream.makeStream();
			Runnable tr = evalCommand(command, scope,
					in, out,
					_in, _out);
			Timer timer = timers[i] = enableProfiling ? new Timer() : null;
			runnables[i] = () -> {
				try {
					if (enableProfiling) {
						timerStack.get().push(timer);
						timer.start();
					}
					if (enableDebug)
						callStack.get().push("command " + command.asString()
							+ "\n\tat " + command.file + ":" + command.line);
					tr.run();
				} finally {
					if (enableDebug) {
						if (runnables.length > 1) callStack.get().clear();
						else callStack.get().pop();
					}
					if (enableProfiling) {
						timer.stop();
						timerStack.get().pop();
					}
					// sulje virta jos se on putki tai muulla tavalla uudelleenohjaus
					if (!last || redirected)
						_out.finish();
				}
			};
			_in = _out;
			i++;
		}

		if (enableProfiling) {
			ArrayDeque<Timer> ts = timerStack.get();
			if (!ts.isEmpty()) {
				ts.peek().stop();
			}
		}
		try {
			if (runnables.length == 1) runnables[0].run();
			else {
				Future<?>[] futures = new Future<?>[runnables.length];
				i = 0;
				for (Runnable r : runnables) {
					futures[i++] = executor.submit(r);
				}
				List<ExecutionException> exceptions = new ArrayList<>();
				try {
					i = futures.length;
					while (i --> 0) {
						try {
							futures[i].get();
						} catch (ExecutionException e) {
							exceptions.add(e);
						}
					}
				} catch (InterruptedException e) {
					error(e);
				} 
				
				if (!exceptions.isEmpty()) {
					error(exceptions.stream().map(e -> {
						if (e.getCause() instanceof RödaException) {
							return (RödaException) e.getCause();
						}
						if (e.getCause() instanceof ReturnException) {
							return createRödaException(leakyPipeErrorRecord, "cannot pipe a return command");
						}
						if (e.getCause() instanceof BreakOrContinueException) {
							return createRödaException(leakyPipeErrorRecord, "cannot pipe a break or continue command");
						}
						return createRödaException(e.getCause());
					}).toArray(n -> new RödaException[n]));
				}
			}
		}
		finally {
			if (enableProfiling) {
				ArrayDeque<Timer> ts = timerStack.get();
				if (!ts.isEmpty()) {
					Timer timer = ts.peek();
					for (Timer t : timers) timer.add(t);
					timer.start();
				}
			}
		}
	}

	@SuppressWarnings("serial")
	private static class BreakOrContinueException extends RuntimeException {
		private boolean isBreak;
		private BreakOrContinueException(boolean isBreak) { this.isBreak = isBreak; }
	}
	private static final BreakOrContinueException BREAK_EXCEPTION = new BreakOrContinueException(true);
	private static final BreakOrContinueException CONTINUE_EXCEPTION = new BreakOrContinueException(false);

	private List<RödaValue> flattenArguments(List<ArgumentTree> arguments,
			RödaScope scope,
			RödaStream in, RödaStream out,
			boolean canResolve) {
		List<RödaValue> args = new ArrayList<>();
		for (ArgumentTree arg : arguments) {
			RödaValue value = evalExpression(arg.expr, scope, in, out, true);
			if (canResolve || arg.flattened) {
				while (value.is(REFERENCE)) value = value.impliciteResolve();
			}
			if (arg.flattened) {
				checkList("*", value);
				args.addAll(value.list());
			}
			else args.add(value);
		}
		return args;
	}

	private Map<String, RödaValue> kwargsToMap(List<KwArgumentTree> arguments,
			RödaScope scope,
			RödaStream in, RödaStream out,
			boolean canResolve) {
		Map<String, RödaValue> map = new HashMap<>();
		for (KwArgumentTree arg : arguments) {
			RödaValue value = evalExpression(arg.expr, scope, in, out, true);
			if (canResolve) value = value.impliciteResolve();
			map.put(arg.name, value);
		}
		return map;
	}

	public Runnable evalCommand(Command cmd,
			RödaScope scope,
			RödaStream in, RödaStream out,
			RödaStream _in, RödaStream _out) {
		
		if (cmd.type == Command.Type.NORMAL) {
			RödaValue function = evalExpression(cmd.name, scope, in, out);
			List<Datatype> typeargs = cmd.typearguments.stream()
					.map(scope::substitute).collect(toList());
			List<RödaValue> args = flattenArguments(cmd.arguments.arguments, scope, in, out, false);
			Map<String, RödaValue> kwargs = kwargsToMap(cmd.arguments.kwarguments, scope, in, out, false);
			Runnable r = () -> {
				exec(cmd.file, cmd.line, function, typeargs, args, kwargs, scope, _in, _out);
			};
			return r;
		}
		
		if (cmd.type == Command.Type.INTERLEAVE) {
			// TODO tee monisäikeinen versio?
			return () -> {
				int size = cmd.cmds.size();
				int i = 0;
				
				RödaStream[] __outs = new RödaStream[size];
				for (Command icmd : cmd.cmds) {
					RödaStream __in = RödaStream.makeEmptyStream();
					__outs[i] = RödaStream.makeStream();
					evalCommand(icmd, scope, in, out, __in, __outs[i]).run();
					i++;
				}
				
				for (RödaStream __out : __outs) __out.finish();
				
				i = 0;
				while (true) {
					RödaValue val = __outs[i].pull();
					if (val == null) break;
					_out.push(val);
					i++; i %= size;
				}
				for (RödaStream __out : __outs) if (__out.open()) streamError("streams of mixed length");
			};
		}
		
		if (cmd.type == Command.Type.DEL) {
			ExpressionTree e = cmd.name;
			if (e.type != ExpressionTree.Type.ELEMENT
					&& e.type != ExpressionTree.Type.SLICE)
				error("bad lvalue for del: " + e.asString());
			if (e.type == ExpressionTree.Type.ELEMENT) {
				return () -> {
					RödaValue list = evalExpression(e.sub, scope, in, out).impliciteResolve();
					RödaValue index = evalExpression(e.index, scope, in, out)
							.impliciteResolve();
					list.del(index);
				};
			}
			else if (e.type == ExpressionTree.Type.SLICE) {
				return () -> {
					RödaValue list = evalExpression(e.sub, scope, in, out).impliciteResolve();
					RödaValue index1 = e.index1 == null ? null :
						evalExpression(e.index1, scope, in, out)
							.impliciteResolve();
					RödaValue index2 = e.index2 == null ? null :
						evalExpression(e.index2, scope, in, out)
							.impliciteResolve();
					RödaValue step = e.step == null ? null :
						evalExpression(e.step, scope, in, out)
							.impliciteResolve();
					list.delSlice(index1, index2, step);
				};
			}
		}

		if (cmd.type == Command.Type.VARIABLE) {
			List<RödaValue> args = flattenArguments(cmd.arguments.arguments, scope, in, out, true);
			ExpressionTree e = cmd.name;
			if (e.type != ExpressionTree.Type.VARIABLE
					&& e.type != ExpressionTree.Type.ELEMENT
					&& e.type != ExpressionTree.Type.SLICE
					&& e.type != ExpressionTree.Type.FIELD)
				error("bad lvalue for '" + cmd.operator + "': " + e.asString());
			Consumer<RödaValue> assign, assignLocal;
			if (e.type == ExpressionTree.Type.VARIABLE) {
				assign = v -> {
					RödaValue value = scope.resolve(e.variable);
					if (value == null || !value.is(REFERENCE))
						value = RödaReference.of(e.variable, scope, e.file, e.line);
					value.assign(v);
				};
				assignLocal = v -> {
					RödaValue value = scope.resolve(e.variable);
					if (value == null || !value.is(REFERENCE))
						value = RödaReference.of(e.variable, scope, e.file, e.line);
					value.assignLocal(v);
				};
			}
			else if (e.type == ExpressionTree.Type.ELEMENT) {
				assign = v -> {
					RödaValue list = evalExpression(e.sub, scope, in, out).impliciteResolve();
					RödaValue index = evalExpression(e.index, scope, in, out).impliciteResolve();
					list.set(index, v);
				};
				assignLocal = assign;
			}
			else if (e.type == ExpressionTree.Type.SLICE) {
				assign = v -> {
					RödaValue list = evalExpression(e.sub, scope, in, out).impliciteResolve();
					RödaValue index1 = e.index1 == null ? null :
						evalExpression(e.index1, scope, in, out).impliciteResolve();
					RödaValue index2 = e.index2 == null ? null :
						evalExpression(e.index2, scope, in, out).impliciteResolve();
					RödaValue step = e.step == null ? null :
						evalExpression(e.step, scope, in, out).impliciteResolve();
					list.setSlice(index1, index2, step, v);
				};
				assignLocal = assign;
			}
			else {
				assign = v -> {
					RödaValue record = evalExpression(e.sub, scope, in, out).impliciteResolve();
					record.setField(e.field, v);
				};
				assignLocal = assign;
			}
			Supplier<RödaValue> resolve = () -> evalExpression(e, scope, in, out).impliciteResolve();
			Runnable r;
			switch (cmd.operator) {
			case ":=": {
				r = () -> {
					checkArgs(":=", 1, args.size());
					assignLocal.accept(args.get(0));
				};
			} break;
			case "=": {
				r = () -> {
					checkArgs("=", 1, args.size());
					assign.accept(args.get(0));
				};
			} break;
			case "++": {
				r = () -> {
					RödaValue v = resolve.get();
					checkNumber("++", v);
					checkArgs("++", 0, args.size());
					if (v.is(INTEGER))
						assign.accept(RödaInteger.of(v.integer()+1));
					else if (v.is(FLOATING))
						assign.accept(RödaFloating.of(v.floating()+1.0));
				};
			} break;
			case "--": {
				r = () -> {
					RödaValue v = resolve.get();
					checkNumber("--", v);
					checkArgs("--", 0, args.size());
					if (v.is(INTEGER))
						assign.accept(RödaInteger.of(v.integer()-1));
					else if (v.is(FLOATING))
						assign.accept(RödaFloating.of(v.floating()-1.0));
				};
			} break;
			case "+=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkListOrNumber("+=", v);
					if (v.is(LIST)) {
						args.forEach(v::add);
					}
					else {
						checkArgs("+=", 1, args.size());
						assign.accept(v.callOperator(CType.ADD, args.get(0)));
					}
				};
			} break;
			case "-=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkListOrNumber("-=", v);
					if (v.is(LIST)) {
						args.forEach(v::remove);
					}
					else {
						checkArgs("-=", 1, args.size());
						assign.accept(v.callOperator(CType.SUB, args.get(0)));
					}
				};
			} break;
			case "*=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkArgs("*=", 1, args.size());
					assign.accept(v.callOperator(CType.MUL, args.get(0)));
				};
			} break;
			case "/=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkArgs("/=", 1, args.size());
					assign.accept(v.callOperator(CType.DIV, args.get(0)));
				};
			} break;
			case "//=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkArgs("//=", 1, args.size());
					assign.accept(v.callOperator(CType.IDIV, args.get(0)));
				};
			} break;
			case "%=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkArgs("%=", 1, args.size());
					assign.accept(v.callOperator(CType.MOD, args.get(0)));
				};
			} break;
			case "^=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkArgs("^=", 1, args.size());
					assign.accept(v.callOperator(CType.POW, args.get(0)));
				};
			} break;
			case ".=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkListOrString(".=", v);
					if (v.is(LIST)) {
						for (RödaValue arg : args) {
							checkList(".=", arg);
							v.addAll(arg.list());
						}
					}
					else {
						checkArgs(".=", 1, args.size());
						assign.accept(RödaString.of(v.str()+args.get(0).str()));
					}
				};
			} break;
			case "~=": {
				r = () -> {
					RödaValue rval = resolve.get();
					checkString("~=", rval);
					boolean quoteMode = false; // TODO: päätä, pitääkö tämä toteuttaa myöhemmin
					if (args.size() % 2 != 0) illegalArguments("illegal arguments for '~=': even number required (got " + (args.size()-1) + ")");
					String text = rval.str();
					try {
						for (int j = 0; j < args.size(); j+=2) {
							checkString(e.asString() + "~=", args.get(j));
							checkString(e.asString() + "~=", args.get(j+1));
							Pattern pattern = args.get(j).pattern();
							String replacement = args.get(j+1).str();
							if (quoteMode) replacement = Matcher
									.quoteReplacement(replacement);
							text = pattern.matcher(text).replaceAll(replacement);
						}
					} catch (PatternSyntaxException ex) {
						error("'"+e.asString()+"~=': pattern syntax exception: "
								+ ex.getMessage());
					}
					assign.accept(RödaString.of(text));
					return;
				};
			} break;
			case "?": {
				r = () -> {
					checkArgs("?", 0, args.size());
					if (e.type != ExpressionTree.Type.VARIABLE)
						error("bad lvalue for '?': " + e.asString());
					_out.push(RödaBoolean.of(scope.resolve(e.variable) != null));
				};
			} break;
			default:
				unknownName("unknown operator " + cmd.operator);
				r = null;
			}
			Runnable finalR = () -> {
				if (enableDebug) {
					callStack.get().push("variable command " + e.asString() + " " + cmd.operator + " "
						+ args.stream()
							.map(RödaValue::str)
							.collect(joining(" "))
						+ "\n\tat " + cmd.file + ":" + cmd.line);
				}
				try {
					r.run();
				}
				catch (RödaException ex) { throw ex; }
				catch (Throwable ex) { error(ex); }
				finally {
					if (enableDebug) callStack.get().pop();
				}
			};
			return finalR;
		}

		if (cmd.type == Command.Type.WHILE || cmd.type == Command.Type.IF) {
			boolean isWhile = cmd.type == Command.Type.WHILE;
			boolean neg = cmd.negation;
			String commandName = isWhile?(neg?"until":"while"):(neg?"unless":"if");
			Runnable r = () -> {
				boolean goToElse = true;
				do {
					RödaScope newScope = new RödaScope(scope);
					if (evalCond(commandName, cmd.cond, scope, _in) ^ neg) break;
					goToElse = false;
					try {
						for (StatementTree s : cmd.body) {
							evalStatement(s, newScope, _in, _out, false);
						}
					} catch (BreakOrContinueException e) {
						if (!isWhile) throw e;
						if (e.isBreak) break;
					}
				} while (isWhile);
				if (goToElse && cmd.elseBody != null) {
					RödaScope newScope = new RödaScope(scope);
					for (StatementTree s : cmd.elseBody) {
						evalStatement(s, newScope, _in, _out, false);
					}
				}
			};
			return r;
		}

		if (cmd.type == Command.Type.FOR) {
			Runnable r;
			if (cmd.list != null) {
				if (cmd.variables.size() != 1) error("invalid for statement: there must be only 1 variable when iterating a list");
				RödaValue list = evalExpression(cmd.list, scope, in, out).impliciteResolve();
				checkList("for", list);
				r = () -> {
					for (RödaValue val : list.list()) {
						RödaScope newScope = new RödaScope(scope);
						newScope.setLocal(cmd.variables.get(0), val);
						if (cmd.cond != null && evalCond("for if", cmd.cond, newScope, _in))
							continue;
						try {
							for (StatementTree s : cmd.body) {
								evalStatement(s, newScope, _in, _out, false);
							}
						} catch (BreakOrContinueException e) {
							if (e.isBreak) break;
						}
					}
				};
			} else {
				r = () -> {
					String firstVar = cmd.variables.get(0);
					List<String> otherVars = cmd.variables.subList(1, cmd.variables.size());
					while (true) {
						RödaValue val = _in.pull();
						if (val == null) break;

						RödaScope newScope = new RödaScope(scope);
						newScope.setLocal(firstVar, val);
						for (String var : otherVars) {
							val = _in.pull();

							if (val == null) {
								String place = "for loop: "
										+ "for " + cmd.variables.stream().collect(joining(", "))
										+ (cmd.list != null ? " in " + cmd.list.asString() : "")
										+ " at " + cmd.file + ":" + cmd.line;
								emptyStream("empty stream (in " + place + ")");
							}
							newScope.setLocal(var, val);
						}

						if (cmd.cond != null
								&& evalCond("for if", cmd.cond, newScope, _in))
							continue;
						try {
							for (StatementTree s : cmd.body) {
								evalStatement(s, newScope, _in, _out, false);
							}
						} catch (BreakOrContinueException e) {
							if (e.isBreak) break;
						}
					}
				};
			}
			return r;
		}

		if (cmd.type == Command.Type.TRY_DO) {
			Runnable r = () -> {
				try {
					RödaScope newScope = new RödaScope(scope);
					for (StatementTree s : cmd.body) {
						evalStatement(s, newScope, _in, _out, false);
					}
				} catch (ReturnException e) {
					throw e;
				} catch (BreakOrContinueException e) {
					throw e;
				} catch (Exception e) {
					if (cmd.variable != null) {
						RödaScope newScope = new RödaScope(scope);
						RödaValue errorObject;
						if (e instanceof RödaException)
							errorObject = ((RödaException) e).getErrorObject();
						else errorObject = makeErrorObject(javaErrorRecord, e.getClass().getName() + ": "
								+ e.getMessage(),
								e.getStackTrace());
						newScope.setLocal(cmd.variable, errorObject);
						for (StatementTree s : cmd.elseBody) {
							evalStatement(s, newScope, _in, _out, false);
						}
					}
				}
			};
			return r;
		}

		if (cmd.type == Command.Type.TRY) {
			Runnable tr = evalCommand(cmd.cmd, scope, in, out, _in, _out);
			Runnable r = () -> {
				try {
					tr.run();
				} catch (ReturnException e) {
					throw e;
				} catch (BreakOrContinueException e) {
					throw e;
				} catch (Exception e) {} // virheet ohitetaan TODO virheenkäsittely
			};
			return r;
		}

		if (cmd.type == Command.Type.RETURN) {
			if (!cmd.arguments.kwarguments.isEmpty())
				illegalArguments("all arguments of return must be non-kw");
			List<RödaValue> args = flattenArguments(cmd.arguments.arguments, scope, in, out, true);
			Runnable r = () -> {
				for (RödaValue arg : args) out.push(arg);
				throw RETURN_EXCEPTION;
			};
			return r;
		}

		if (cmd.type == Command.Type.BREAK
				|| cmd.type == Command.Type.CONTINUE) {
			Runnable r = () -> {
				throw cmd.type == Command.Type.BREAK ? BREAK_EXCEPTION : CONTINUE_EXCEPTION;
			};
			return r;
		}

		if (cmd.type == Command.Type.EXPRESSION) {
			return () -> {
				_out.push(evalExpression(cmd.name, scope, _in, _out));
			};
		}

		unknownName("unknown command");
		return null;
	}

	private boolean evalCond(String cmd, StatementTree cond, RödaScope scope, RödaStream in) {
		RödaStream condOut = RödaStream.makeStream();
		evalStatement(cond, scope, in, condOut, true);
		boolean brk = false;
		while (true) {
			RödaValue val = condOut.pull();
			if (val == null) break;
			checkBoolean(cmd, val);
			brk = brk || !val.bool();
		}
		return brk;
	}

	private RödaValue evalExpression(ExpressionTree exp, RödaScope scope, RödaStream in, RödaStream out) {
		return evalExpressionWithoutErrorHandling(exp, scope, in, out, false);
	}

	private RödaValue evalExpression(ExpressionTree exp, RödaScope scope, RödaStream in, RödaStream out,
			boolean variablesAreReferences) {
		if (enableDebug) callStack.get().push("expression " + exp.asString() + "\n\tat " + exp.file + ":" + exp.line);
		RödaValue value;
		try {
			value = evalExpressionWithoutErrorHandling(exp, scope, in, out,
					variablesAreReferences);
		}
		catch (RödaException e) { throw e; }
		catch (ReturnException e) { throw e; }
		catch (Throwable e) { error(e); value = null; }
		finally {
			if (enableDebug) callStack.get().pop();
		}
		return value;
	}

	@SuppressWarnings("incomplete-switch")
	private RödaValue evalExpressionWithoutErrorHandling(ExpressionTree exp, RödaScope scope,
			RödaStream in, RödaStream out,
			boolean variablesAreReferences) {
		if (exp.type == ExpressionTree.Type.STRING) return RödaString.of(exp.string);
		if (exp.type == ExpressionTree.Type.PATTERN) return RödaString.of(exp.pattern);
		if (exp.type == ExpressionTree.Type.INTEGER) return RödaInteger.of(exp.integer);
		if (exp.type == ExpressionTree.Type.FLOATING) return RödaFloating.of(exp.floating);
		if (exp.type == ExpressionTree.Type.BLOCK) return RödaFunction.of(treeToFunction(exp.block, scope), scope);
		if (exp.type == ExpressionTree.Type.LIST) return RödaList.of(exp.list
				.stream()
				.map(e -> evalExpression(e, scope, in, out).impliciteResolve())
				.collect(toList()));
		if (exp.type == ExpressionTree.Type.REFLECT
				|| exp.type == ExpressionTree.Type.TYPEOF) {
			Datatype type;
			if (exp.type == ExpressionTree.Type.REFLECT) {
				type = scope.substitute(exp.datatype);
			}
			else { // TYPEOF
				RödaValue value = evalExpression(exp.sub, scope, in, out).impliciteResolve();
				type = value.basicIdentity();
			}
			return type.resolveReflection();
		}
		if (exp.type == ExpressionTree.Type.NEW) {
			Datatype type = scope.substitute(exp.datatype);
			List<Datatype> subtypes = exp.datatype.subtypes.stream()
					.map(scope::substitute).collect(toList());
			List<RödaValue> args = exp.list.stream()
					.map(e -> evalExpression(e, scope, in, out))
					.map(RödaValue::impliciteResolve)
					.collect(toList());
			return newRecord(type, subtypes, args, scope);
		}
		if (exp.type == ExpressionTree.Type.LENGTH
				|| exp.type == ExpressionTree.Type.ELEMENT
				|| exp.type == ExpressionTree.Type.SLICE
				|| exp.type == ExpressionTree.Type.CONTAINS) {
			RödaValue list = evalExpression(exp.sub, scope, in, out).impliciteResolve();

			if (exp.type == ExpressionTree.Type.LENGTH) {
				return list.length();
			}

			if (exp.type == ExpressionTree.Type.ELEMENT) {
				RödaValue index = evalExpression(exp.index, scope, in, out).impliciteResolve();
				return list.get(index);
			}

			if (exp.type == ExpressionTree.Type.SLICE) {
				RödaValue start, end, step;

				start = exp.index1 == null ? null :
					evalExpression(exp.index1, scope, in, out).impliciteResolve();
				end = exp.index2 == null ? null :
					evalExpression(exp.index2, scope, in, out).impliciteResolve();
				step = exp.step == null ? null :
					evalExpression(exp.step, scope, in, out).impliciteResolve();

				return list.slice(start, end, step);
			}

			if (exp.type == ExpressionTree.Type.CONTAINS) {
				RödaValue index = evalExpression(exp.index, scope, in, out).impliciteResolve();
				return list.contains(index);
			}
		}
		if (exp.type == ExpressionTree.Type.FIELD) {
			return evalExpression(exp.sub, scope, in, out).impliciteResolve()
					.getField(exp.field);
		}
		if (exp.type == ExpressionTree.Type.CONCAT) {
			RödaValue val1 = evalExpression(exp.exprA, scope, in, out).impliciteResolve();
			RödaValue val2 = evalExpression(exp.exprB, scope, in, out).impliciteResolve();
			if (val1.is(LIST) && val2.is(LIST)) {
				List<RödaValue> newList = new ArrayList<>();
				newList.addAll(val1.list());
				newList.addAll(val2.list());
				return RödaList.of(newList);
			}
			else return RödaString.of(val1.str() + val2.str());
		}
		if (exp.type == ExpressionTree.Type.CONCAT_CHILDREN) {
			RödaValue val1 = evalExpression(exp.exprA, scope, in, out).impliciteResolve();
			RödaValue val2 = evalExpression(exp.exprB, scope, in, out).impliciteResolve();
			return concat(val1, val2);
		}
		if (exp.type == ExpressionTree.Type.JOIN) {
			RödaValue list = evalExpression(exp.exprA, scope, in, out).impliciteResolve();
			RödaValue separator = evalExpression(exp.exprB, scope, in, out).impliciteResolve();
			return list.join(separator);
		}
		if (exp.type == ExpressionTree.Type.IS) {
			Datatype type = scope.substitute(exp.datatype);
			RödaValue value = evalExpression(exp.sub, scope, in, out).impliciteResolve();
			return RödaBoolean.of(value.is(type));
		}
		if (exp.type == ExpressionTree.Type.IN) {
			RödaValue value = evalExpression(exp.exprA, scope, in, out).impliciteResolve();
			RödaValue list = evalExpression(exp.exprB, scope, in, out).impliciteResolve();
			return list.containsValue(value);
		}
		if (exp.type == ExpressionTree.Type.STATEMENT_LIST) {
			RödaStream _out = RödaStream.makeStream();
			evalStatement(exp.statement, scope, in, _out, true);
			RödaValue val = _out.readAll();
			if (val == null)
				emptyStream("empty stream (in statement expression: " + exp.asString() + ")");
			return val;
		}
		if (exp.type == ExpressionTree.Type.STATEMENT_SINGLE) {
			RödaStream _out = RödaStream.makeStream();
			evalStatement(exp.statement, scope, in, _out, true);
			RödaValue val = _out.readAll();
			if (val.list().isEmpty())
				emptyStream("empty stream (in statement expression: " + exp.asString() + ")");
			if (val.list().size() > 1)
				fullStream("stream is full (in statement expression: " + exp.asString() + ")");
			return val.list().get(0);
		}
		if (exp.type == ExpressionTree.Type.VARIABLE) {
			if (variablesAreReferences) {
				return RödaReference.of(exp.variable, scope, exp.file, exp.line);
			}
			RödaValue v = scope.resolve(exp.variable);
			if (v == null) unknownName("variable not found: " + exp.variable);
			return v;
		}
		if (exp.type == ExpressionTree.Type.CALCULATOR) {
			if (exp.isUnary) {
				RödaValue sub = evalExpression(exp.sub, scope, in, out).impliciteResolve();
				return sub.callOperator(exp.ctype, null);
			}
			else {
				RödaValue val1 = evalExpression(exp.exprA, scope, in, out).impliciteResolve();
				RödaValue val2 = null;
				Supplier<RödaValue> getVal2 = () -> evalExpression(exp.exprB, scope, in, out)
						.impliciteResolve();
				switch (exp.ctype) {
				case AND:
					if (!val1.is(BOOLEAN)) typeMismatch("tried to AND " + val1.typeString());
					if (val1.bool() == false) return RödaBoolean.of(false);
					val2 = getVal2.get();
					if (!val2.is(BOOLEAN)) typeMismatch("tried to AND " + val2.typeString());
					return RödaBoolean.of(val2.bool());
				case OR:
					if (!val1.is(BOOLEAN)) typeMismatch("tried to OR " + val1.typeString());
					if (val1.bool() == true) return RödaBoolean.of(true);
					val2 = getVal2.get();
					if (!val2.is(BOOLEAN)) typeMismatch("tried to OR " + val2.typeString());
					return RödaBoolean.of(val1.bool() || val2.bool());
				case XOR:
					if (!val1.is(BOOLEAN)) typeMismatch("tried to XOR " + val1.typeString());
					val2 = getVal2.get();
					if (!val2.is(BOOLEAN)) typeMismatch("tried to XOR " + val2.typeString());
					return RödaBoolean.of(val1.bool() ^ val2.bool());
				}
				val2 = getVal2.get();
				return val1.callOperator(exp.ctype, val2);
			}
		}

		unknownName("unknown expression type " + exp.type);
		return null;
	}
	
	private RödaValue newRecord(Datatype type, List<Datatype> subtypes, List<RödaValue> args, RödaScope scope) {
		//if (enableProfiling) pushTimer();
		switch (type.name) {
		case "list":
			if (subtypes.size() > 1)
				illegalArguments("wrong number of typearguments to 'list': 1 required, got " + subtypes.size());
			if (args.size() == 0) {
				if (subtypes.size() == 0)
					return RödaList.empty();
				else if (subtypes.size() == 1)
					return RödaList.empty(subtypes.get(0));
			}
			else if (args.size() == 1) {
				checkList("list", args.get(0));
				if (subtypes.size() == 0)
					return RödaList.of(args.get(0).list());
				else if (subtypes.size() == 1)
					return RödaList.of(subtypes.get(0), args.get(0).list());
				argumentOverflow("list", 1, args.size());
			}
			return null;
		case "map":
			if (subtypes.size() == 0)
				return RödaMap.empty();
			else if (subtypes.size() == 1)
				return RödaMap.empty(subtypes.get(0));
			illegalArguments("wrong number of typearguments to 'map': 1 required, got " + subtypes.size());
			return null;
		case "namespace":
			if (subtypes.size() == 0)
				return RödaNamespace.empty();
			illegalArguments("wrong number of typearguments to 'namespace': 0 required, got " + subtypes.size());
			return null;
		}
		//if (enableProfiling) popTimer("<new>");
		return newRecord(null, type, subtypes, args, scope);
	}

	private RödaValue newRecord(RödaValue value,
			Datatype type, List<Datatype> subtypes, List<RödaValue> args, RödaScope scope) {
		Record r = type.resolve();
		RödaScope declarationScope = r.declarationScope;
		if (r.typeparams.size() != subtypes.size())
			illegalArguments("wrong number of typearguments for '" + r.name + "': "
					+ r.typeparams.size() + " required, got " + subtypes.size());
		if (r.params.size() != args.size())
			illegalArguments("wrong number of arguments for '" + r.name + "': "
					+ r.params.size() + " required, got " + args.size());
		value = value != null ? value : RödaRecordInstance.of(r, subtypes);
		RödaScope recordScope = new RödaScope(declarationScope);
		recordScope.setLocal("self", value);
		for (int i = 0; i < subtypes.size(); i++) {
			recordScope.addTypearg(r.typeparams.get(i), subtypes.get(i));
		}
		for (int i = 0; i < args.size(); i++) {
			recordScope.setLocal(r.params.get(i), args.get(i));
		}
		for (Record.SuperExpression superExp : r.superTypes) {
			Datatype superType = scope.substitute(superExp.type);
			List<Datatype> superSubtypes = superExp.type.subtypes.stream()
					.map(recordScope::substitute).collect(toList());
			List<RödaValue> superArgs = superExp.args.stream()
					.map(e -> evalExpression(e, recordScope,
							RödaStream.makeEmptyStream(), RödaStream.makeEmptyStream()))
					.map(RödaValue::impliciteResolve)
					.collect(toList());
			newRecord(value, superType, superSubtypes, superArgs, scope);
		}
		for (Record.Field f : r.fields) {
			if (f.defaultValue != null) {
				value.setField(f.name, evalExpression(f.defaultValue, recordScope,
						RödaStream.makeEmptyStream(), RödaStream.makeEmptyStream(), false));
			}
		}
		return value;
	}

	private RödaValue concat(RödaValue val1, RödaValue val2) {
		if (val1.is(LIST) && val2.is(LIST)) {
			List<RödaValue> newList = new ArrayList<>();
			for (RödaValue valA : val1.list()) {
				for (RödaValue valB : val2.list()) {
					newList.add(concat(valA, valB));
				}
			}
			return RödaList.of(newList);
		}
		if (val1.is(LIST)) {
			List<RödaValue> newList = new ArrayList<>();
			for (RödaValue val : val1.list()) {
				newList.add(concat(val, val2));
			}
			return RödaList.of(newList);
		}
		if (val2.is(LIST)) {
			List<RödaValue> newList = new ArrayList<>();
			for (RödaValue val : val2.list()) {
				newList.add(concat(val1, val));
			}
			return RödaList.of(newList);
		}
		return RödaString.of(val1.str()+val2.str());
	}
}
