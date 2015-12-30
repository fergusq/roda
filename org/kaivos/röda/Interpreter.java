package org.kaivos.röda;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Iterator;

import java.util.Optional;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.joining;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutionException;

import java.util.regex.PatternSyntaxException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.RödaValue.*;
import static org.kaivos.röda.Parser.*;

import org.kaivos.nept.parser.ParsingException;

public class Interpreter {
	
	/*** INTERPRETER ***/

	public static abstract class RödaStream implements Iterable<RödaValue> {
		StreamHandler inHandler;
		StreamHandler outHandler;

		abstract RödaValue get();
		abstract void put(RödaValue value);
		abstract void finish();
		abstract boolean finished();
		
		final void push(RödaValue value) {
			inHandler.handlePush(this::finished, this::put, value);
		}

		final RödaValue pull() {
			return outHandler.handlePull(this::finished, this::get);
		}

		final RödaValue readAll() {
			return outHandler.handleReadAll(this::finished, this::get);
		}

		@Override
		public Iterator<RödaValue> iterator() {
			return new Iterator<RödaValue>() {
				RödaValue buffer;
				{
					buffer = pull();
				}

				@Override public boolean hasNext() {
					return buffer != null;
				}

				@Override public RödaValue next() {
					RödaValue tmp = buffer;
					buffer = pull();
					return tmp;
				}
			};
		}
	}

	public static RödaStream makeStream(StreamType in, StreamType out) {
		 RödaStream stream = new RödaStreamImpl();
		 stream.inHandler = in.newHandler();
		 stream.outHandler = out.newHandler();
		 return stream;
	}

	/* pitää kirjaa virroista debug-viestejä varten */
	private static int streamCounter;
	
	private static class RödaStreamImpl extends RödaStream {
		BlockingQueue<RödaValue> queue = new LinkedBlockingQueue<>();
		boolean finished = false;

		@Override
		RödaValue get() {
			//System.err.println("<PULL " + this + ">");
			while (queue.isEmpty() && !finished);

			if (finished()) return null;
			try {
				return queue.take();
			} catch (InterruptedException e) {
				error("threading error");
				return null;
			}
		}

		@Override
		void put(RödaValue value) {
			//System.err.println("<PUSH " + value + " to " + this + ">");
			queue.add(value);
		}

		@Override
		boolean finished() {
			return finished && queue.isEmpty();
		}

		@Override
		void finish() {
			finished = true;
		}

		@Override
		public String toString() {
			return ""+(char)('A'+id);
		}

		int id; { id = streamCounter++; }
	}
	
	static abstract class StreamType {
		abstract StreamHandler newHandler();
	}
	private interface StreamHandler {
		void handlePush(Supplier<Boolean> finished, Consumer<RödaValue> put, RödaValue value);
		RödaValue handlePull(Supplier<Boolean> finished, Supplier<RödaValue> get);
		RödaValue handleReadAll(Supplier<Boolean> finished, Supplier<RödaValue> get);
	}
	static class LineStream extends StreamType {
		String sep;
		LineStream() { sep="\n"; }
		LineStream(String sep) { this.sep=sep; }
		StreamHandler newHandler() {
			return new StreamHandler() {
				StringBuffer buffer = new StringBuffer();
				RödaValue valueBuffer;
				public void handlePush(Supplier<Boolean> finished,
						       Consumer<RödaValue> put,
						       RödaValue value) {
					if (!value.isString()) {
						put.accept(value);
						return;
					}
					for (String line : value.str().split(sep)) {
						put.accept(valueFromString(line));
					}
				}
				public RödaValue handlePull(Supplier<Boolean> finished,
							    Supplier<RödaValue> get) {
					if (valueBuffer != null) {
						RödaValue val = valueBuffer;
						valueBuffer = null;
						return val;
					}
					do {
						RödaValue val = get.get();
						if (!val.isString()) {
							if (buffer.length() != 0) {
								valueBuffer = val;
								return valueFromString(buffer.toString());
							}
							return val;
						}
						String str = val.str();
						if (str.indexOf(sep) >= 0) {
							String ans = str.substring(0, str.indexOf(sep));
							ans = buffer.toString() + ans;
							buffer = new StringBuffer(str.substring(str.indexOf(sep)+1));
							return valueFromString(ans);
						}
						buffer = buffer.append(str);
					} while (true);
				}
				public RödaValue handleReadAll(Supplier<Boolean> finished,
							Supplier<RödaValue> get) {
					StringBuilder all = new StringBuilder();
					while (!finished.get()) {
						RödaValue val = get.get();
						if (val == null) break;
						all = all.append(val.str());
					}
					return valueFromString(all.toString());
				}
				
			};
		}
	}
	static class VoidStream extends StreamType {
		StreamHandler newHandler() {
			return new StreamHandler() {
				public void handlePush(Supplier<Boolean> finished,
						       Consumer<RödaValue> put,
						       RödaValue value) {
					// nop
				}
				public RödaValue handlePull(Supplier<Boolean> finished,
							    Supplier<RödaValue> get) {
					error("can't pull from a void stream");
					return null;
				}
				public RödaValue handleReadAll(Supplier<Boolean> finished,
							       Supplier<RödaValue> get) {
					error("can't pull from a void stream");
					return null;
				}
			};
		}
	}
	static class ByteStream extends StreamType {
		StreamHandler newHandler() {
			return new StreamHandler() {
				List<Character> queue = new ArrayList<>();
				public void handlePush(Supplier<Boolean> finished,
						       Consumer<RödaValue> put,
						       RödaValue value) {
					if (!value.isString()) {
						put.accept(value);
						return;
					}
					for (char chr : value.str().toCharArray())
						put.accept(valueFromString(String.valueOf(chr)));
				}
				public RödaValue handlePull(Supplier<Boolean> finished,
							    Supplier<RödaValue> get) {
					if (queue.isEmpty()) {
						RödaValue val = get.get();
						if (!val.isString()) return val;
						for (char chr : val.str().toCharArray())
							queue.add(chr);
					}
					return valueFromString(String.valueOf(queue.remove(0)));
				}
				public RödaValue handleReadAll(Supplier<Boolean> finished,
							       Supplier<RödaValue> get) {
					StringBuilder all = new StringBuilder();
					while (!finished.get()) {
						RödaValue val = get.get();
						if (val == null) break;
						all = all.append(val.str());
					}
					return valueFromString(all.toString());
				}
			};
		}
	}
	static class BooleanStream extends StreamType {
		StreamHandler newHandler() {
			return new StreamHandler() {
				public void handlePush(Supplier<Boolean> finished,
						       Consumer<RödaValue> put,
						       RödaValue value) {
					put.accept(value);
				}
				public RödaValue handlePull(Supplier<Boolean> finished,
							    Supplier<RödaValue> get) {
					RödaValue value = get.get();
					return valueFromBoolean(value.bool());
				}
				public RödaValue handleReadAll(Supplier<Boolean> finished,
							       Supplier<RödaValue> get) {
					boolean all = true;
					while (!finished.get()) {
						RödaValue val = get.get();
						if (val == null) break;
						all &= val.bool();
					}
					return valueFromBoolean(all);
				}
			};
		}
	}
	static class ValueStream extends StreamType {
		StreamHandler newHandler() {
			return new StreamHandler() {
				public void handlePush(Supplier<Boolean> finished,
						       Consumer<RödaValue> put,
						       RödaValue value) {
					put.accept(value);
				}
				public RödaValue handlePull(Supplier<Boolean> finished,
							    Supplier<RödaValue> get) {
					return get.get();
				}
				public RödaValue handleReadAll(Supplier<Boolean> finished,
							       Supplier<RödaValue> get) {
				        List<RödaValue> list = new ArrayList<RödaValue>();
					while (true) {
						RödaValue val = get.get();
						if (val == null) break;
						list.add(val);
					}
					return valueFromList(list);
				}
			};
		}
	}
	
	public static class RödaScope {
		Optional<RödaScope> parent;
		Map<String, RödaValue> map;
		RödaScope(Optional<RödaScope> parent) {
			this.parent = parent;
			this.map = new HashMap<>();
		}
		RödaScope(RödaScope parent) {
			this(Optional.of(parent));
		}

		synchronized RödaValue resolve(String name) {
			if (map.get(name) != null) return map.get(name);
			if (parent.isPresent()) return parent.get().resolve(name);
			return null;
		}

		synchronized void set(String name, RödaValue value) {
			if (parent.isPresent() && parent.get().resolve(name) != null)
				parent.get().set(name, value);
			else map.put(name, value);
		}

		synchronized void setLocal(String name, RödaValue value) {
			map.put(name, value);
		}
	}
	
	public RödaScope G = new RödaScope(Optional.empty());

	private RödaStream STDIN, STDOUT;

	private void initializeIO() {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		STDIN = new RödaStream() {
				{
					inHandler = new ValueStream().newHandler();
					outHandler = new ValueStream().newHandler();
				}
				public RödaValue get() {
					try {
						String line = in.readLine();
						if (line == null) return null;
						else return valueFromString(line);
					} catch (IOException e) {
						e.printStackTrace();
						error("io error");
						return null;
					}
				}
				public void put(RödaValue val) {
					error("no output to input");
				}
				public boolean finished() {
					return false; // TODO ehkä jotain oikeita tarkistuksia EOF:n varalta?
				}
				public void finish() {
					// nop
				}
				public String toString(){return"STDIN";}
			};
		
		STDOUT = new RödaStream() {
				{
					inHandler = new ValueStream().newHandler();
					outHandler = new ValueStream().newHandler();
				}
				public RödaValue get() {
					error("no input from output");
					return null;
				}
				public void put(RödaValue val) {
					System.out.print(val.str());
				}
				public boolean finished() {
					return finished;
				}
				boolean finished = false;
				public void finish() {
					finished = true;
				}
				public String toString(){return"STDOUT";}
			};
	}

	{ Builtins.populate(G); initializeIO(); /*System.out.println(G.map.keySet());*/ }

	public static ThreadLocal<ArrayDeque<String>> callStack = new InheritableThreadLocal<ArrayDeque<String>>() {
			@Override protected ArrayDeque<String> childValue(ArrayDeque<String> parentValue) {
				return new ArrayDeque<>(parentValue);
			}
		};

	static { callStack.set(new ArrayDeque<>()); }

	@SuppressWarnings("serial")
	private static class FatalException extends RuntimeException {
		private FatalException(String message) {
			super(message);
		}
	}
	
	static void error(String message) {
		System.err.println("FATAL ERROR: " + message);
		printStackTrace();
		throw new FatalException(message);
	}

	private static void printStackTrace() {
		ArrayDeque<String> stack = callStack.get();
		Iterator<String> iterator = stack.iterator();
		while (iterator.hasNext()) {
			System.err.println(iterator.next());
		}
	}
	
	public void interpret(String code) {
		interpret(code, "<input>");
	}
	
	public void interpret(String code, String filename) {
		try {
			callStack.get().clear();
			
			Program program = parse(t.tokenize(code, filename));
			for (Function f : program.functions) {
				G.setLocal(f.name, valueFromFunction(f));
			}
			
			RödaValue main = G.resolve("main");
			if (main == null) return;
			
			exec("<runtime>", 0, main, new ArrayList<>(), G, STDIN, STDOUT);
		} catch (FatalException e) {
			// viesti on jo tulostettu
		} catch (ParsingException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			printStackTrace();
		}
	}

	public void interpretStatement(String code, String filename) {
		try {
			callStack.get().clear();
			
			Statement statement = parseStatement(t.tokenize(code, filename));
			evalStatement(statement, G, STDIN, STDOUT, false);
		} catch (FatalException e) {
			// viesti on jo tulostettu
		} catch (ParsingException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
		        printStackTrace();
		} 
	}
	
	private boolean isReferenceParameter(RödaValue function, int i) {
		assert function.isFunction();
		boolean isNative = function.isNativeFunction();
		List<Parameter> parameters = isNative ? function.nfunction.parameters : function.function.parameters;
		boolean isVarargs = isNative ? function.nfunction.isVarargs : function.function.isVarargs;
		if (isVarargs && i >= parameters.size()-1) return parameters.get(parameters.size()-1).reference;
		else if (i >= parameters.size()) return false; // tästä tulee virhe myöhemmin
		else return parameters.get(i).reference;
	}

	static void checkReference(String function, RödaValue arg) {
	        if (!arg.isReference()) {
			error("illegal argument for '" + function
			      + "': reference expected (got " + arg.typeString() + ")");
		}
	}
	
	static void checkList(String function, RödaValue arg) {
	        if (!arg.isList()) {
			error("illegal argument for '" + function
			      + "': list expected (got " + arg.typeString() + ")");
		}
	}
	
	static void checkString(String function, RödaValue arg) {
	        if (!arg.isString()) {
			error("illegal argument for '" + function
			      + "': string expected (got " + arg.typeString() + ")");
		}
	}

	static void checkNumber(String function, RödaValue arg) {
	        if (!arg.isNumber()) {
			error("illegal argument for '" + function
			      + "': number expected (got " + arg.typeString() + ")");
		}
	}
	
	static void checkArgs(String function, int required, int got) {
		if (got > required) argumentOverflow(function, required, got);
		if (got < required) argumentUnderflow(function, required, got);
	}
	
	static void argumentOverflow(String function, int required, int got) {
		error("illegal number of arguments for '" + function
		      + "': at most " + required + " required (got " + got + ")");
	}

	static void argumentUnderflow(String function, int required, int got) {
		error("illegal number of arguments for '" + function
		      + "': at least " + required + " required (got " + got + ")");
	}

	private void exec(String file, int line,
			  RödaValue value, List<RödaValue> rawArgs, RödaScope scope,
			  RödaStream in, RödaStream out) {
		List<RödaValue> args = new ArrayList<>();
		int i = 0;
		for (RödaValue val : rawArgs) {
			if (val.isReference()
			    && !(value.isFunction()
				&& isReferenceParameter(value, i))) {
				RödaValue rval = val.resolve(true);
				if (rval == null) error("variable not found (via implicite reference): " + val.target);
				args.add(rval);
			}
			else if (val.isList()) {
				args.add(val.copy());
			}
			else args.add(val);
			i++;
		}
		
		if (args.size() > 0) {
			callStack.get().push("calling " + value.str()
					     + " with arguments " + args.stream()
					     .map(RödaValue::str).collect(joining(", "))
					     + "\n\tat " + file + ":" + line);
		}
		else {
			callStack.get().push("calling " + value.str()
					     + " with no arguments\n"
					     + "\tat " + file + ":" + line);
		}
		execWithoutErrorHandling(value, rawArgs, args, scope, in, out);
		callStack.get().pop();
	}
	
	public void execWithoutErrorHandling(RödaValue value, List<RödaValue> rawArgs, List<RödaValue> args,
					     RödaScope scope, RödaStream in, RödaStream out) {
		
		//callStack.push("exec " + value + "("+args+") " + in + " -> " + out);
		if (value.isReference()) {
			if (args.isEmpty()) {
				RödaValue rval = value.resolve(false);
				if (rval == null) error("variable not found (via explicite reference): " + value.target);
				if (rval.isList())
					for (RödaValue item : rval.list)
						out.push(item);
				else out.push(rval);
				return;
			}
			if (args.size() == 1) {
				RödaValue rval = value.resolve(false);
				if (args.get(0).str().equals("-inc")) {
					checkNumber("-inc", rval);
					value.assign(valueFromInt(rval.num()+1));
					return;
				}
				if (args.get(0).str().equals("-dec")) {
					checkNumber("-dec", rval);
					value.assign(valueFromInt(rval.num()+1));
					return;
				}
			}
			if (args.size() == 2) {
				if (args.get(0).str().equals("-set")) {
					value.assign(args.get(1));
					return;
				}
				if (args.get(0).str().equals("-create")) {
					value.assignLocal(args.get(1));
					return;
				}
				if (args.get(0).str().equals("-add")) {
					RödaValue rval = value.resolve(false);
					checkList("-add", rval);
					rval.list.add(args.get(1));
					return;
				}
				if (args.get(0).str().equals("-append")) {
					RödaValue rval = value.resolve(false);
					checkString("-append", rval);
					value.assign(valueFromString(rval.str() + args.get(1).str()));
					return;
				}
			}
			if (args.size() == 3) {
				if (args.get(0).str().equals("-put")) {
					RödaValue rval = value.resolve(false);
					checkList("-put", rval);
					checkNumber(value.target + " -put", args.get(1));
					int index = args.get(1).num();
					rval.list.set(index, args.get(2));
					return;
				}
			}
			if (args.get(0).str().equals("-replace")) {
				if (args.size() % 2 != 1) error("invalid arguments for -replace: even number required (got " + (args.size()-1) + ")");
				RödaValue rval = value.resolve(false);
				checkString("-replace", rval);
				
				String text = rval.str();

				try {
					for (int j = 1; j < args.size(); j+=2) {
						checkString(value.target + " -replace", args.get(j));
						checkString(value.target + " -replace", args.get(j+1));
						String pattern = args.get(j).str();
						String replacement = args.get(j+1).str();
						text = text.replaceAll(pattern, replacement);
					}
				} catch (PatternSyntaxException e) {
					error("replace: pattern syntax error: " + e.getMessage());
				}
				value.assign(valueFromString(text));
				return;
			}
			error("illegal arguments for a variable '" + value.target + "': " + args.stream().map(a->a.str()).collect(joining(" ")) + "; perhaps you tried to call a function that doesn't exist?");
			return;
		}
		if (value.isList()) {
			for (RödaValue item : value.list)
				out.push(item);
			return;
		}
		if (value.isFunction() && !value.isNativeFunction()) {
			boolean isVarargs = value.function.isVarargs;
			List<Parameter> parameters = value.function.parameters;
			String name = value.function.name;
			if (!value.function.isVarargs) {
				checkArgs(name, parameters.size(), args.size());
			} else {
				if (args.size() < parameters.size()-1)
					argumentUnderflow(name, parameters.size()-1, args.size());
			}
			// joko nimettömän funktion paikallinen scope tai tämä scope
			RödaScope newScope = new RödaScope(value.scope == null ? scope : value.scope);
			int j = 0;
			for (Parameter p : parameters) {
				if (isVarargs && j == parameters.size()-1) break;
				newScope.setLocal(p.name, args.get(j++));
			}
			if (isVarargs) {
				RödaValue argslist = valueFromList(new ArrayList<>());
				if (args.size() >= parameters.size()) {
					for (int k = parameters.size()-1; k < args.size(); k++) {
						argslist.list.add(args.get(k));
					}
				}
				newScope.setLocal(parameters.get(parameters.size()-1).name, argslist);
			}
			for (Statement s : value.function.body) {
				evalStatement(s, newScope, in, out, false);
			}
			return;
		}
		if (value.isNativeFunction()) {
			if (!value.nfunction.isVarargs) {
				checkArgs(value.nfunction.name, value.nfunction.parameters.size(), args.size());
			}
			value.nfunction.body.exec(rawArgs, args, scope, in, out);
			return;
		}
		error("can't execute a value of type " + value.typeString());
	}

	private ExecutorService executor = Executors.newCachedThreadPool();
	
	private void evalStatement(Statement statement, RödaScope scope,
				  RödaStream in, RödaStream out, boolean redirected) {
		RödaStream _in = in;
		int i = 0;
		Runnable[] runnables = new Runnable[statement.commands.size()];
		for (Command command : statement.commands) {
			boolean last = i == statement.commands.size()-1;
			RödaStream _out = last ? out : new RödaStreamImpl();
			Pair<Runnable, StreamType> tr = evalCommand(command, scope,
								  in, out,
								  _in, _out,
								  !last || redirected);
			runnables[i] = tr.first();
			if (i != 0) {
				_in.outHandler = tr.second().newHandler();
				_in.inHandler = new ValueStream().newHandler();
			}
			_in = _out;
			i++;
		}
		i = 0;
		if (runnables.length == 1) runnables[0].run();
		else {
			Future<?>[] futures = new Future<?>[runnables.length];
			for (Runnable r : runnables) {
				futures[i++] = executor.submit(r);
			}
			try {
				i = futures.length;
				while (i --> 0) {
					futures[i].get();
				}
			} catch (InterruptedException|ExecutionException e) {
				e.printStackTrace();
				error("java exception");
			}
		}
	}

	private static class Pair<T, U> {
		private final T t;
		private final U u;
		public Pair(T t, U u) {
			this.t = t;
			this.u = u;
		}
		public T first() {
			return t;
		}
		public U second() {
			return u;
		}
	}
	
	private static class Trair<T, U, V> {
		private final T t;
		private final U u;
		private final V v;
		public Trair(T t, U u, V v) {
			this.t = t;
			this.u = u;
			this.v = v;
		}
		public T first() {
			return t;
		}
		public U second() {
			return u;
		}
		public V third() {
			return v;
		}
	}
	
	public Pair<Runnable, StreamType> evalCommand(Command cmd,
						      RödaScope scope,
						      RödaStream in, RödaStream out,
						      RödaStream _in, RödaStream _out,
						      boolean canFinish) {
		if (cmd.type == Command.Type.NORMAL) {
				RödaValue function = evalExpression(cmd.name, scope, in, out);
				List<RödaValue> args = cmd.arguments.stream().map(a -> evalExpression(a, scope, in, out)).collect(toList());
				Runnable r = () -> {
					exec(cmd.file, cmd.line, function, args, scope, _in, _out);
					if (canFinish) _out.finish();
				};
				StreamType ins;
				if (function.isFunction() && !function.isNativeFunction()) {
					ins = function.function.input;
				} else ins = new ValueStream();
				return new Pair<>(r, ins);
		}
		
		if (cmd.type == Command.Type.WHILE || cmd.type == Command.Type.IF) {
			boolean isWhile = cmd.type == Command.Type.WHILE;
			Runnable r = () -> {
				RödaScope newScope = new RödaScope(scope);
				do {
					RödaStream condOut = makeStream(new ValueStream(), new BooleanStream());
					evalStatement(cmd.cond, scope, _in, condOut, true);
					if (!condOut.pull().bool()) break;
					for (Statement s : cmd.body) {
						evalStatement(s, newScope, _in, _out, false);
					}
				} while (isWhile);
				if (canFinish) _out.finish();
			};
			return new Pair<>(r, new ValueStream());
		}

		if (cmd.type == Command.Type.FOR) {
			RödaValue list = evalExpression(cmd.list, scope, in, out).impliciteResolve();
			checkList("for", list);
			Runnable r = () -> {
				RödaScope newScope = new RödaScope(scope);
				for (RödaValue val : list.list) {
					newScope.setLocal(cmd.variable, val);
					for (Statement s : cmd.body) {
						evalStatement(s, newScope, _in, _out, false);
					}
				}
				if (canFinish) _out.finish();
			};
			return new Pair<>(r, new ValueStream());
		}

		if (cmd.type == Command.Type.TRY_DO) {
			Runnable r = () -> {
				try {
					RödaScope newScope = new RödaScope(scope);
					for (Statement s : cmd.body) {
						evalStatement(s, newScope, _in, _out, false);
					}
				} catch (Exception e) {} // virheet ohitetaan TODO virheenkäsittely
				if (canFinish) _out.finish();
			};
			return new Pair<>(r, new ValueStream());
		}

		if (cmd.type == Command.Type.TRY) {
			Runnable r = () -> {
				try {
				        evalCommand(cmd.cmd, scope, in, out, _in, _out, false);
				} catch (Exception e) {} // virheet ohitetaan TODO virheenkäsittely
				if (canFinish) _out.finish();
			};
			return new Pair<>(r, new ValueStream());
		}


		error("unknown command");
		return null;
	}

	private RödaValue evalExpression(Expression exp, RödaScope scope, RödaStream in, RödaStream out) {
		callStack.get().push("expression " + exp.type + "\n\tat " + exp.file + ":" + exp.line);
		RödaValue value = evalExpressionWithoutErrorHandling(exp, scope, in, out);
		callStack.get().pop();
		return value;
	}
	
	private RödaValue evalExpressionWithoutErrorHandling(Expression exp, RödaScope scope, RödaStream in, RödaStream out) {
		if (exp.type == Expression.Type.STRING) return valueFromString(exp.string);
		if (exp.type == Expression.Type.NUMBER) return valueFromInt(exp.number);
		if (exp.type == Expression.Type.BLOCK) return valueFromFunction(exp.block, scope);
		if (exp.type == Expression.Type.LIST) return valueFromList(exp.list
									   .stream()
									   .map(e
										->
										evalExpression(e, scope, in, out)
										.impliciteResolve()
										)
									   .collect(toList()));
		if (exp.type == Expression.Type.LENGTH
		    || exp.type == Expression.Type.ELEMENT
		    || exp.type == Expression.Type.SLICE) {
			RödaValue list = evalExpression(exp.sub, scope, in, out).impliciteResolve();
			
			if (exp.type == Expression.Type.LENGTH) {
				if (list.isString()) {
					return valueFromInt(list.str().length());
				}
				if (!list.isList()) error("a " + list.typeString() + " doesn't have a length!");
				return valueFromInt(list.list.size());
			}

			if (!list.isList()) error("a " + list.typeString() + " doesn't have elements!");
			
			if (exp.type == Expression.Type.ELEMENT) {
				int index = evalExpression(exp.index, scope, in, out).impliciteResolve().num();
				if (index < 0) index = list.list.size()+index;
				if (list.list.size() <= index) error("array index out of bounds: index " + index + ", size " + list.list.size());
				return list.list.get(index);
			}

			if (exp.type == Expression.Type.SLICE) {
				int start, end;

				if (exp.index1 != null)
					start = evalExpression(exp.index1, scope, in, out)
						.impliciteResolve().num();
				else start = 0;

				if (exp.index2 != null)
					end = evalExpression(exp.index2, scope, in, out)
						.impliciteResolve().num();
				else end = list.list.size();
				
			        if (start < 0) start = list.list.size()+start;
				if (end < 0) end = list.list.size()+end;
				if (end == 0 && start > 0) end = list.list.size();

				return valueFromList(list.list.subList(start, end));
			}
		}
		if (exp.type == Expression.Type.CONCAT) {
			RödaValue val1 = evalExpression(exp.exprA, scope, in, out).impliciteResolve();
			RödaValue val2 = evalExpression(exp.exprB, scope, in, out).impliciteResolve();
			return concat(val1, val2);
		}
		if (exp.type == Expression.Type.JOIN) {
			RödaValue list = evalExpression(exp.exprA, scope, in, out).impliciteResolve();
			String separator = evalExpression(exp.exprB, scope, in, out).impliciteResolve().str();
			if (!list.isList()) error("can't join a " + list.typeString());
			String text = "";
			int i = 0; for (RödaValue val : list.list) {
				if (i++ != 0) text += separator;
				text += val.str();
			}
			return valueFromString(text);
		}
		if (exp.type == Expression.Type.STATEMENT) {
			RödaStream _out = makeStream(new ValueStream(), new ValueStream());
			evalStatement(exp.statement, scope, in, _out, true);
			return _out.readAll();
		}
		if (exp.type == Expression.Type.VARIABLE) {
			RödaValue v = scope.resolve(exp.variable);
			if (v == null) return valueFromReference(scope, exp.variable);
			if (!v.isFunction()) {
				return valueFromReference(scope, exp.variable);
			}
			return v;
		}

		error("unknown expression type " + exp.type);
		return null;
	}

	private RödaValue concat(RödaValue val1, RödaValue val2) {
		if (val1.isList()) {
			List<RödaValue> newList = new ArrayList<>();
			for (RödaValue val : val1.list) {
				newList.add(concat(val, val2));
			}
			return valueFromList(newList);
		}
		if (val2.isList()) {
			List<RödaValue> newList = new ArrayList<>();
			for (RödaValue val : val2.list) {
				newList.add(concat(val1, val));
			}
			return valueFromList(newList);
		}
		return valueFromString(val1.str()+val2.str());
	}
}
