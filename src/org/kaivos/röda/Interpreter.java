package org.kaivos.röda;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;

import java.util.Optional;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.joining;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.IOException;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.RödaValue.*;
import org.kaivos.röda.type.RödaRecordInstance;
import org.kaivos.röda.type.RödaList;
import org.kaivos.röda.type.RödaMap;
import org.kaivos.röda.type.RödaString;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaFloating;
import org.kaivos.röda.type.RödaBoolean;
import org.kaivos.röda.type.RödaFunction;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaReference;
import org.kaivos.röda.RödaStream;
import static org.kaivos.röda.RödaStream.ISLineStream;
import static org.kaivos.röda.RödaStream.OSStream;
import static org.kaivos.röda.Parser.*;

import org.kaivos.nept.parser.ParsingException;
import org.kaivos.nept.parser.TokenList;

public class Interpreter {

	/*** INTERPRETER ***/

	public static class RödaScope {
		Optional<RödaScope> parent;
		Map<String, RödaValue> map;
		Map<String, Datatype> typeargs;
		RödaScope(Optional<RödaScope> parent) {
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
			if (parent.isPresent() && parent.get().resolve(name) != null)
				parent.get().set(name, value);
			else {
				map.put(name, value);
			}
		}

		public synchronized void setLocal(String name, RödaValue value) {
			map.put(name, value);
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
			return new Datatype(type.name, subtypes);
		}
	}

	public RödaScope G = new RödaScope(Optional.empty());
	public Map<String, Record> records = new HashMap<>();
	Map<String, RödaValue> typeReflections = new HashMap<>();

	RödaStream STDIN, STDOUT;

	public File currentDir = new File(System.getProperty("user.dir"));

	private void initializeIO() {
		InputStreamReader ir = new InputStreamReader(System.in);
		BufferedReader in = new BufferedReader(ir);
		PrintWriter out = new PrintWriter(System.out);
		STDIN = new ISLineStream(in);
		STDOUT = new OSStream(out);
	}

	private static final Record errorRecord, typeRecord, fieldRecord;
	static {
		errorRecord = new Record("Error",
				Collections.emptyList(),
				Collections.emptyList(),
				Arrays.asList(new Record.Field("message", new Datatype("string")),
						new Record.Field("stack",
								new Datatype("list", Arrays.asList(new Datatype("string")))),
						new Record.Field("javastack",
								new Datatype("list", Arrays.asList(new Datatype("string")))),
						new Record.Field("causes",
								new Datatype("list", Arrays.asList(new Datatype("Error"))))
						),
				false);
		typeRecord = new Record("Type",
				Collections.emptyList(),
				Collections.emptyList(),
				Arrays.asList(new Record.Field("name", new Datatype("string")),
						new Record.Field("annotations", new Datatype("list")),
						new Record.Field("fields",
								new Datatype("list", Arrays.asList(new Datatype("Field")))),
						new Record.Field("newInstance", new Datatype("function"))
						),
				false);
		fieldRecord = new Record("Field",
				Collections.emptyList(),
				Collections.emptyList(),
				Arrays.asList(new Record.Field("name", new Datatype("string")),
						new Record.Field("annotations", new Datatype("list")),
						new Record.Field("type", new Datatype("Type")),
						new Record.Field("get", new Datatype("function")),
						new Record.Field("set", new Datatype("function"))
						),
				false);
	}

	{
		Builtins.populate(this);
		preRegisterRecord(errorRecord);
		preRegisterRecord(typeRecord);
		preRegisterRecord(fieldRecord);
		postRegisterRecord(errorRecord);
		postRegisterRecord(typeRecord);
		postRegisterRecord(fieldRecord);

		G.setLocal("ENV", RödaMap.of(System.getenv().entrySet().stream()
				.collect(toMap(e -> e.getKey(),
						e -> RödaString.of(e.getValue())))));
	}

	public void preRegisterRecord(Record record) {
		records.put(record.name, record);
		typeReflections.put(record.name, createRecordClassReflection(record));
	}

	public void postRegisterRecord(Record record) {
		createFieldReflections(record, typeReflections.get(record.name));
	}

	private RödaValue createRecordClassReflection(Record record) {
		RödaValue typeObj = RödaRecordInstance.of(typeRecord, Collections.emptyList(), records);
		typeObj.setField("name", RödaString.of(record.name));
		typeObj.setField("annotations", evalAnnotations(record.annotations));
		typeObj.setField("newInstance", RödaNativeFunction
				.of("Type.newInstance",
						(ta, a, k, s, i, o) -> {
							o.push(newRecord(new Datatype(record.name), ta, a));
						}, Collections.emptyList(), false));
		return typeObj;
	}

	private void createFieldReflections(Record record, RödaValue typeObj) {
		typeObj.setField("fields", RödaList.of("Field", record.fields.stream()
				.map(f -> createFieldReflection(record, f))
				.collect(toList())));
	}

	private RödaValue createFieldReflection(Record record, Record.Field field) {
		RödaValue fieldObj = RödaRecordInstance.of(fieldRecord, Collections.emptyList(), records);
		fieldObj.setField("name", RödaString.of(field.name));
		fieldObj.setField("annotations", evalAnnotations(field.annotations));
		fieldObj.setField("get", RödaNativeFunction
				.of("Field.get",
						(ta, a, k, s, i, o) -> {
							RödaValue obj = a.get(0);
							if (!obj.is(new Datatype(record.name))) {
								error("illegal argument for Field.get: "
										+ record.name + " required, got " + obj.typeString());
							}
							o.push(obj.getField(field.name));
						}, Arrays.asList(new Parameter("object", false)), false));
		fieldObj.setField("set", RödaNativeFunction
				.of("Field.set",
						(ta, a, k, s, i, o) -> {
							RödaValue obj = a.get(0);
							if (!obj.is(new Datatype(record.name))) {
								error("illegal argument for Field.get: "
										+ record.name + " required, got " + obj.typeString());
							}
							RödaValue val = a.get(1);
							obj.setField(field.name, val);
						}, Arrays.asList(new Parameter("object", false),
								new Parameter("value", false)), false));
		if (typeReflections.containsKey(field.type.name))
			fieldObj.setField("type", typeReflections.get(field.type.name));
		return fieldObj;
	}

	private RödaValue evalAnnotations(List<Annotation> annotations) {
		return annotations.stream()
				.map(a -> {
					RödaValue function = G.resolve(a.name);
					List<RödaValue> args = flattenArguments(a.args.arguments, G,
							RödaStream.makeEmptyStream(),
							RödaStream.makeStream(),
							false);
					Map<String, RödaValue> kwargs = kwargsToMap(a.args.kwarguments, G,
							RödaStream.makeEmptyStream(),
							RödaStream.makeStream(), false);
					RödaStream _out = RödaStream.makeStream();
					exec(a.file, a.line, function, Collections.emptyList(), args, kwargs,
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

	public Interpreter() {
		initializeIO();
	}

	public Interpreter(RödaStream in, RödaStream out) {
		STDIN = in;
		STDOUT = out;
	}

	public static ThreadLocal<ArrayDeque<String>> callStack = new InheritableThreadLocal<ArrayDeque<String>>() {
		@Override protected ArrayDeque<String> childValue(ArrayDeque<String> parentValue) {
			return new ArrayDeque<>(parentValue);
		}
	};

	static { callStack.set(new ArrayDeque<>()); }
	
	public boolean enableDebug = true;

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

	private static RödaValue makeErrorObject(String message, StackTraceElement[] javaStackTrace, Throwable... causes) {
		RödaValue errorObject = RödaRecordInstance
				.of(errorRecord,
						Collections.emptyList(),
						new HashMap<>()); // Purkkaa, mutta toimii: errorilla ei ole riippuvuuksia
		errorObject.setField("message", RödaString.of(message));
		errorObject.setField("stack", RödaList.of("string", callStack.get().stream()
				.map(RödaString::of).collect(toList())));
		errorObject.setField("javastack", RödaList.of("string", Arrays.stream(javaStackTrace)
				.map(StackTraceElement::toString).map(RödaString::of)
				.collect(toList())));
		errorObject.setField("causes", RödaList.of("Error", Arrays.stream(causes)
				.map(cause -> cause instanceof RödaException ? ((RödaException) cause).getErrorObject()
						: makeErrorObject(cause.getClass().getName() + ": " + cause.getMessage(), cause.getStackTrace()))
				.collect(toList())));
		return errorObject;
	}

	private static RödaException createRödaException(String message) {
		RödaValue errorObject = makeErrorObject(message, Thread.currentThread().getStackTrace());
		return new RödaException(message, new ArrayDeque<>(callStack.get()), errorObject);
	}
	
	public static void error(String message) {
		throw createRödaException(message);
	}
	
	private static RödaException createRödaException(Throwable...causes) {
		if (causes.length == 1 && causes[0] instanceof RödaException) return (RödaException) causes[0];
		
		String message;
		if (causes.length == 1) message = causes[0].getClass().getName() + ": " + causes[0].getMessage();
		else message = "multiple threads crashed";
		
		StackTraceElement[] javaStackTrace;
		if (causes.length == 1) javaStackTrace = causes[0].getStackTrace();
		else javaStackTrace = Thread.currentThread().getStackTrace();
		
		RödaValue errorObject = makeErrorObject(message, javaStackTrace, causes);
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

	public void interpret(String code) {
		interpret(code, "<input>");
	}

	public void interpret(String code, String filename) {
		interpret(code, new ArrayList<>(), "<input>");
	}

	public void interpret(String code, List<RödaValue> args, String filename) {
		try {
			load(code, filename, G);

			RödaValue main = G.resolve("main");
			if (main == null) return;
			if (!main.is(FUNCTION) || main.is(NFUNCTION))
				error("The variable 'main' must be a function");

			exec("<runtime>", 0, main, Collections.emptyList(), args, Collections.emptyMap(), G, STDIN, STDOUT);
		} catch (ParsingException|RödaException e) {
			throw e;
		} catch (Exception e) {
			error(e);
		}
	}

	public void load(String code, String filename, RödaScope scope) {
		try {
			Program program = parse(t.tokenize(code, filename));
			for (Function f : program.blocks) {
				exec("<runtime>", 0, RödaFunction.of(f),
						Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(),
						G, RödaStream.makeEmptyStream(), RödaStream.makeStream());
			}
			for (Function f : program.functions) {
				scope.setLocal(f.name, RödaFunction.of(f));
			}
			for (Record r : program.records) {
				preRegisterRecord(r);
			}
			for (Record r : program.records) {
				postRegisterRecord(r);
			}
		} catch (ParsingException|RödaException e) {
			throw e;
		} catch (Exception e) {
			error(e);
		}
	}

	public void loadFile(File file, RödaScope scope) {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			String code = "";
			String line = "";
			while ((line = in.readLine()) != null) {
				code += line + "\n";
			}
			in.close();
			load(code, file.getName(), scope);
		} catch (IOException e) {
			error(e);
		}
	}

	public void interpretStatement(String code, String filename) {
		try {
			TokenList tl = t.tokenize(code, filename);
			Statement statement = parseStatement(tl);
			tl.accept("<EOF>");
			evalStatement(statement, G, STDIN, STDOUT, false);
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
		return Collections.emptyList();
	}

	public static void checkReference(String function, RödaValue arg) {
		if (!arg.is(REFERENCE)) {
			error("illegal argument for '" + function
					+ "': reference expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkList(String function, RödaValue arg) {
		if (!arg.is(LIST)) {
			error("illegal argument for '" + function
					+ "': list expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkMap(String function, RödaValue arg) {
		if (!arg.is(MAP)) {
			error("illegal argument for '" + function
					+ "': list expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkListOrString(String function, RödaValue arg) {
		if (!arg.is(LIST) && !arg.is(STRING)) {
			error("illegal argument for '" + function
					+ "': list or string expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkListOrNumber(String function, RödaValue arg) {
		if (!arg.is(LIST) && !arg.is(INTEGER)) {
			error("illegal argument for '" + function
					+ "': list or integer expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkString(String function, RödaValue arg) {
		if (!arg.is(STRING)) {
			error("illegal argument for '" + function
					+ "': string expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkNumber(String function, RödaValue arg) {
		if (!arg.is(INTEGER)) {
			error("illegal argument for '" + function
					+ "': integer expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkBoolean(String function, RödaValue arg) {
		if (!arg.is(BOOLEAN)) {
			error("illegal argument for '" + function
					+ "': boolean expected (got " + arg.typeString() + ")");
		}
	}

	public static void checkFunction(String function, RödaValue arg) {
		if (!arg.is(FUNCTION)) {
			error("illegal argument for '" + function
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
				error("illegal argument for '"+name+"': " + t + " expected (got "
						+ args.get(i).typeString() + ")");
		}

		for (Parameter par : kwparameters) {
			if (!kwargs.containsKey(par.name))
				error("illegal arguments for '" + name + "': kw argument " + par.name + " not found");
			if (par.type == null) continue;
			Datatype t = scope.substitute(par.type);
			if (!kwargs.get(par.name).is(t))
				error("illegal argument for '"+name+"': " + t + " expected (got "
						+ kwargs.get(par.name).typeString() + ")");
		}
	}

	public static void argumentOverflow(String function, int required, int got) {
		error("illegal number of arguments for '" + function
				+ "': at most " + required + " required (got " + got + ")");
	}

	public static void argumentUnderflow(String function, int required, int got) {
		error("illegal number of arguments for '" + function
				+ "': at least " + required + " required (got " + got + ")");
	}

	public void exec(String file, int line,
			RödaValue value, List<Datatype> typeargs,
			List<RödaValue> rawArgs, Map<String, RödaValue> rawKwArgs,
			RödaScope scope, RödaStream in, RödaStream out) {
		List<RödaValue> args = new ArrayList<>();
		Map<String, RödaValue> kwargs = new HashMap<>();
		int i = 0;
		for (RödaValue val : rawArgs) {
			if (val.is(REFERENCE)
					&& !(value.is(FUNCTION)
							&& isReferenceParameter(value, i))) {
				RödaValue rval = val.resolve(true);
				if (rval.is(REFERENCE)) rval = rval.resolve(true);
				args.add(rval);
			}
			else if (val.is(REFERENCE)
					&& value.is(FUNCTION)
					&& isReferenceParameter(value, i)) {
				RödaValue rval = val.unsafeResolve();
				if (rval != null && rval.is(REFERENCE)) args.add(rval);
				else args.add(val);
			}
			else if (val.is(LIST)) {
				args.add(val.copy());
			}
			else args.add(val);
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
			if (val.is(REFERENCE)) {
				RödaValue rval = val.resolve(true);
				if (rval.is(REFERENCE)) rval = rval.resolve(true);
				kwargs.put(kwpar.name, rval);
			}
			else if (val.is(LIST)) {
				kwargs.put(kwpar.name, val.copy());
			}
			else kwargs.put(kwpar.name, val);
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
			if (enableDebug) callStack.get().pop();
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
			RödaScope newScope = value.localScope() == null ? new RödaScope(G) : new RödaScope(value.localScope());
			if (typeparams.size() != typeargs.size())
				error("illegal number of typearguments for '" + name + "': "
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
			for (Statement s : value.function().body) {
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
		error("can't execute a value of type " + value.typeString());
	}

	private void evalStatement(Statement statement, RödaScope scope,
			RödaStream in, RödaStream out, boolean redirected) {
		RödaStream _in = in;
		int i = 0;
		Runnable[] runnables = new Runnable[statement.commands.size()];
		for (Command command : statement.commands) {
			boolean last = i == statement.commands.size()-1;
			RödaStream _out = last ? out : RödaStream.makeStream();
			Runnable tr = evalCommand(command, scope,
					in, out,
					_in, _out);
			runnables[i] = () -> {
				try {
					tr.run();
				} finally {
					// sulje virta jos se on putki tai muulla tavalla uudelleenohjaus
					if (!last || redirected)
						_out.finish();
				}
			};
			_in = _out;
			i++;
		}
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
						return createRödaException("cannot pipe a return command");
					}
					if (e.getCause() instanceof BreakOrContinueException) {
						return createRödaException("cannot pipe a break or continue command");
					}
					return createRödaException(e.getCause());
				}).toArray(n -> new RödaException[n]));
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

	private List<RödaValue> flattenArguments(List<Argument> arguments,
			RödaScope scope,
			RödaStream in, RödaStream out,
			boolean canResolve) {
		List<RödaValue> args = new ArrayList<>();
		for (Argument arg : arguments) {
			RödaValue value = evalExpression(arg.expr, scope, in, out, true);
			if (canResolve || arg.flattened) value = value.impliciteResolve();
			if (arg.flattened) {
				checkList("*", value);
				args.addAll(value.list());
			}
			else args.add(value);
		}
		return args;
	}

	private Map<String, RödaValue> kwargsToMap(List<KwArgument> arguments,
			RödaScope scope,
			RödaStream in, RödaStream out,
			boolean canResolve) {
		Map<String, RödaValue> map = new HashMap<>();
		for (KwArgument arg : arguments) {
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

		if (cmd.type == Command.Type.VARIABLE) {
			List<RödaValue> args = flattenArguments(cmd.arguments.arguments, scope, in, out, true);
			Expression e = cmd.name;
			if (e.type != Expression.Type.VARIABLE
					&& e.type != Expression.Type.ELEMENT
					&& e.type != Expression.Type.FIELD)
				error("bad lvalue for '" + cmd.operator + "': " + e.asString());
			Consumer<RödaValue> assign, assignLocal;
			if (e.type == Expression.Type.VARIABLE) {
				assign = v -> {
					RödaValue value = scope.resolve(e.variable);
					if (value == null || !value.is(REFERENCE))
						value = RödaReference.of(e.variable, scope);
					value.assign(v);
				};
				assignLocal = v -> {
					RödaValue value = scope.resolve(e.variable);
					if (value == null || !value.is(REFERENCE))
						value = RödaReference.of(e.variable, scope);
					value.assignLocal(v);
				};
			}
			else if (e.type == Expression.Type.ELEMENT) {
				assign = v -> {
					RödaValue list = evalExpression(e.sub, scope, in, out).impliciteResolve();
					RödaValue index = evalExpression(e.index, scope, in, out)
							.impliciteResolve();
					list.set(index, v);
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
					if (args.size() > 1) argumentOverflow(":=", 1, args.size());
					assignLocal.accept(args.get(0));
				};
			} break;
			case "=": {
				r = () -> {
					if (args.size() > 1) argumentOverflow("=", 1, args.size());
					assign.accept(args.get(0));
				};
			} break;
			case "++": {
				r = () -> {
					RödaValue v = resolve.get();
					checkNumber("++", v);
					assign.accept(RödaInteger.of(v.integer()+1));
				};
			} break;
			case "--": {
				r = () -> {
					RödaValue v = resolve.get();
					checkNumber("--", v);
					assign.accept(RödaInteger.of(v.integer()-1));
				};
			} break;
			case "+=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkListOrNumber("+=", v);
					if (v.is(LIST)) {
						v.add(args.get(0));
					}
					else {
						checkNumber("+=", args.get(0));
						assign.accept(RödaInteger.of(v.integer()+args.get(0).integer()));
					}
				};
			} break;
			case "-=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkNumber("-=", v);
					checkNumber("-=", args.get(0));
					assign.accept(RödaInteger.of(v.integer()-args.get(0).integer()));
				};
			} break;
			case "*=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkNumber("*=", v);
					checkNumber("*=", args.get(0));
					assign.accept(RödaInteger.of(v.integer()*args.get(0).integer()));
				};
			} break;
			case "/=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkNumber("/=", v);
					checkNumber("/=", args.get(0));
					assign.accept(RödaInteger.of(v.integer()/args.get(0).integer()));
				};
			} break;
			case ".=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkListOrString(".=", v);
					if (v.is(LIST)) {
						checkList(".=", args.get(0));
						ArrayList<RödaValue> newList = new ArrayList<>();
						newList.addAll(v.list());
						newList.addAll(args.get(0).list());
						assign.accept(RödaList.of(newList));
					}
					else {
						checkString(".=", args.get(0));
						assign.accept(RödaString.of(v.str()+args.get(0).str()));
					}
				};
			} break;
			case "~=": {
				r = () -> {
					RödaValue rval = resolve.get();
					checkString(".=", rval);
					boolean quoteMode = false; // TODO: päätä, pitääkö tämä toteuttaa myöhemmin
					if (args.size() % 2 != 0) error("illegal arguments for '~=': even number required (got " + (args.size()-1) + ")");
					String text = rval.str();
					try {
						for (int j = 0; j < args.size(); j+=2) {
							checkString(e.asString() + "~=", args.get(j));
							checkString(e.asString() + "~=", args.get(j+1));
							String pattern = args.get(j).str();
							String replacement = args.get(j+1).str();
							if (quoteMode) replacement = Matcher
									.quoteReplacement(replacement);
							text = text.replaceAll(pattern, replacement);
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
					if (e.type != Expression.Type.VARIABLE)
						error("bad lvalue for '?': " + e.asString());
					_out.push(RödaBoolean.of(scope.resolve(e.variable) != null));
				};
			} break;
			default:
				error("unknown operator " + cmd.operator);
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
						for (Statement s : cmd.body) {
							evalStatement(s, newScope, _in, _out, false);
						}
					} catch (BreakOrContinueException e) {
						if (!isWhile) throw e;
						if (e.isBreak) break;
					}
				} while (isWhile);
				if (goToElse && cmd.elseBody != null) {
					RödaScope newScope = new RödaScope(scope);
					for (Statement s : cmd.elseBody) {
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
							for (Statement s : cmd.body) {
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
								error("empty stream in " + place);
							}
							newScope.setLocal(var, val);
						}

						if (cmd.cond != null
								&& evalCond("for if", cmd.cond, newScope, _in))
							continue;
						try {
							for (Statement s : cmd.body) {
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
					for (Statement s : cmd.body) {
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
						else errorObject = makeErrorObject(e.getClass().getName() + ": "
								+ e.getMessage(),
								e.getStackTrace());
						newScope.setLocal(cmd.variable, errorObject);
						for (Statement s : cmd.elseBody) {
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
				error("all arguments of return must be non-kw");
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

		error("unknown command");
		return null;
	}

	private boolean evalCond(String cmd, Statement cond, RödaScope scope, RödaStream in) {
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

	private RödaValue evalExpression(Expression exp, RödaScope scope, RödaStream in, RödaStream out) {
		return evalExpressionWithoutErrorHandling(exp, scope, in, out, false);
	}

	private RödaValue evalExpression(Expression exp, RödaScope scope, RödaStream in, RödaStream out,
			boolean variablesAreReferences) {
		if (enableDebug) callStack.get().push("expression " + exp.asString() + "\n\tat " + exp.file + ":" + exp.line);
		RödaValue value;
		try {
			value = evalExpressionWithoutErrorHandling(exp, scope, in, out,
					variablesAreReferences);
		}
		catch (RödaException e) { throw e; }
		catch (Throwable e) { error(e); value = null; }
		finally {
			if (enableDebug) callStack.get().pop();
		}
		return value;
	}

	@SuppressWarnings("incomplete-switch")
	private RödaValue evalExpressionWithoutErrorHandling(Expression exp, RödaScope scope,
			RödaStream in, RödaStream out,
			boolean variablesAreReferences) {
		if (exp.type == Expression.Type.STRING) return RödaString.of(exp.string);
		if (exp.type == Expression.Type.INTEGER) return RödaInteger.of(exp.integer);
		if (exp.type == Expression.Type.FLOATING) return RödaFloating.of(exp.floating);
		if (exp.type == Expression.Type.BLOCK) return RödaFunction.of(exp.block, scope);
		if (exp.type == Expression.Type.LIST) return RödaList.of(exp.list
				.stream()
				.map(e -> evalExpression(e, scope, in, out).impliciteResolve())
				.collect(toList()));
		if (exp.type == Expression.Type.REFLECT
				|| exp.type == Expression.Type.TYPEOF) {
			Datatype type;
			if (exp.type == Expression.Type.REFLECT) {
				type = scope.substitute(exp.datatype);
			}
			else { // TYPEOF
				RödaValue value = evalExpression(exp.sub, scope, in, out).impliciteResolve();
				type = value.basicIdentity();
			}
			if (!typeReflections.containsKey(type.name))
				error("reflect: unknown record class '" + type + "'");
			return typeReflections.get(type.name);
		}
		if (exp.type == Expression.Type.NEW) {
			Datatype type = scope.substitute(exp.datatype);
			List<Datatype> subtypes = exp.datatype.subtypes.stream()
					.map(scope::substitute).collect(toList());
			List<RödaValue> args = exp.list.stream()
					.map(e -> evalExpression(e, scope, in, out))
					.map(RödaValue::impliciteResolve)
					.collect(toList());
			return newRecord(type, subtypes, args);
		}
		if (exp.type == Expression.Type.LENGTH
				|| exp.type == Expression.Type.ELEMENT
				|| exp.type == Expression.Type.SLICE
				|| exp.type == Expression.Type.CONTAINS) {
			RödaValue list = evalExpression(exp.sub, scope, in, out).impliciteResolve();

			if (exp.type == Expression.Type.LENGTH) {
				return list.length();
			}

			if (exp.type == Expression.Type.ELEMENT) {
				RödaValue index = evalExpression(exp.index, scope, in, out).impliciteResolve();
				return list.get(index);
			}

			if (exp.type == Expression.Type.SLICE) {
				RödaValue start, end;

				if (exp.index1 != null)
					start = evalExpression(exp.index1, scope, in, out)
					.impliciteResolve();
				else start = null;

				if (exp.index2 != null)
					end = evalExpression(exp.index2, scope, in, out)
					.impliciteResolve();
				else end = null;

				return list.slice(start, end);
			}

			if (exp.type == Expression.Type.CONTAINS) {
				RödaValue index = evalExpression(exp.index, scope, in, out).impliciteResolve();
				return list.contains(index);
			}
		}
		if (exp.type == Expression.Type.FIELD) {
			return evalExpression(exp.sub, scope, in, out).impliciteResolve()
					.getField(exp.field);
		}
		if (exp.type == Expression.Type.CONCAT) {
			RödaValue val1 = evalExpression(exp.exprA, scope, in, out).impliciteResolve();
			RödaValue val2 = evalExpression(exp.exprB, scope, in, out).impliciteResolve();
			return concat(val1, val2);
		}
		if (exp.type == Expression.Type.JOIN) {
			RödaValue list = evalExpression(exp.exprA, scope, in, out).impliciteResolve();
			RödaValue separator = evalExpression(exp.exprB, scope, in, out).impliciteResolve();
			return list.join(separator);
		}
		if (exp.type == Expression.Type.IS) {
			Datatype type = scope.substitute(exp.datatype);
			RödaValue value = evalExpression(exp.sub, scope, in, out).impliciteResolve();
			return RödaBoolean.of(value.is(type));
		}
		if (exp.type == Expression.Type.IN) {
			RödaValue value = evalExpression(exp.exprA, scope, in, out).impliciteResolve();
			RödaValue list = evalExpression(exp.exprB, scope, in, out).impliciteResolve();
			return list.containsValue(value);
		}
		if (exp.type == Expression.Type.STATEMENT_LIST) {
			RödaStream _out = RödaStream.makeStream();
			evalStatement(exp.statement, scope, in, _out, true);
			RödaValue val = _out.readAll();
			if (val == null)
				error("empty stream in statement expression: " + exp.asString());
			return val;
		}
		if (exp.type == Expression.Type.STATEMENT_SINGLE) {
			RödaStream _out = RödaStream.makeStream();
			evalStatement(exp.statement, scope, in, _out, true);
			RödaValue val = _out.readAll();
			if (val.list().isEmpty())
				error("empty stream in statement expression: " + exp.asString());
			if (val.list().size() > 1)
				error("stream is full in statement expression: " + exp.asString());
			return val.list().get(0);
		}
		if (exp.type == Expression.Type.VARIABLE) {
			if (variablesAreReferences) {
				return RödaReference.of(exp.variable, scope);
			}
			RödaValue v = scope.resolve(exp.variable);
			if (v == null) error("variable '" + exp.variable + "' not found");
			return v;
		}
		if (exp.type == Expression.Type.CALCULATOR) {
			if (exp.isUnary) {
				RödaValue sub = evalExpression(exp.sub, scope, in, out).impliciteResolve();
				switch (exp.ctype) {
				case NOT:
					if (!sub.is(BOOLEAN)) error("tried to NOT a " + sub.typeString());
					return RödaBoolean.of(!sub.bool());
				case NEG:
					if (!sub.is(INTEGER)) error("tried to NEG a " + sub.typeString());
					return RödaInteger.of(-sub.integer());
				case BNOT:
					if (!sub.is(INTEGER)) error("tried to BNOT a " + sub.typeString());
					return RödaInteger.of(~sub.integer());
				}
			}
			else {
				RödaValue val1 = evalExpression(exp.exprA, scope, in, out).impliciteResolve();
				RödaValue val2 = null;
				Supplier<RödaValue> getVal2 = () -> evalExpression(exp.exprB, scope, in, out)
						.impliciteResolve();
				switch (exp.ctype) {
				case AND:
					if (!val1.is(BOOLEAN)) error("tried to AND a " + val1.typeString());
					if (val1.bool() == false) return RödaBoolean.of(false);
					val2 = getVal2.get();
					if (!val2.is(BOOLEAN)) error("tried to AND a " + val2.typeString());
					return RödaBoolean.of(val2.bool());
				case OR:
					if (!val1.is(BOOLEAN)) error("tried to OR a " + val1.typeString());
					if (val1.bool() == true) return RödaBoolean.of(true);
					val2 = getVal2.get();
					if (!val2.is(BOOLEAN)) error("tried to OR a " + val2.typeString());
					return RödaBoolean.of(val1.bool() || val2.bool());
				case XOR:
					if (!val1.is(BOOLEAN)) error("tried to XOR a " + val1.typeString());
					val2 = getVal2.get();
					if (!val2.is(BOOLEAN)) error("tried to XOR a " + val2.typeString());
					return RödaBoolean.of(val1.bool() ^ val2.bool());
				}
				val2 = getVal2.get();
				return val1.callOperator(exp.ctype, val2);
			}
			error("unknown expression type " + exp.ctype);
			return null;
		}

		error("unknown expression type " + exp.type);
		return null;
	}

	private RödaValue newRecord(Datatype type, List<Datatype> subtypes, List<RödaValue> args) {
		switch (type.name) {
		case "list":
			if (subtypes.size() == 0)
				return RödaList.empty();
			else if (subtypes.size() == 1)
				return RödaList.empty(subtypes.get(0));
			error("wrong number of typearguments to 'list': 1 required, got " + subtypes.size());
			return null;
		case "map":
			if (subtypes.size() == 0)
				return RödaMap.empty();
			else if (subtypes.size() == 1)
				return RödaMap.empty(subtypes.get(0));
			error("wrong number of typearguments to 'map': 1 required, got " + subtypes.size());
			return null;
		}
		Record r = records.get(type.name);
		if (r == null)
			error("record class '" + type.name + "' not found");
		if (r.typeparams.size() != subtypes.size())
			error("wrong number of typearguments for '" + r.name + "': "
					+ r.typeparams.size() + " required, got " + subtypes.size());
		if (r.params.size() != args.size())
			error("wrong number of arguments for '" + r.name + "': "
					+ r.params.size() + " required, got " + args.size());
		RödaValue value = RödaRecordInstance.of(r, subtypes, records);
		RödaScope recordScope = new RödaScope(G);
		recordScope.setLocal("self", value);
		for (int i = 0; i < subtypes.size(); i++) {
			recordScope.addTypearg(r.typeparams.get(i), subtypes.get(i));
		}
		for (int i = 0; i < args.size(); i++) {
			recordScope.setLocal(r.params.get(i), args.get(i));
		}
		for (Record.Field f : r.fields) {
			if (f.defaultValue != null) {
				value.setField(f.name, evalExpression(f.defaultValue, recordScope, RödaStream.makeEmptyStream(), RödaStream.makeStream(), false));
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
