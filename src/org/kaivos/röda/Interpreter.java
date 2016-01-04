package org.kaivos.röda;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Deque;
import java.util.ArrayDeque;

import java.util.Optional;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.joining;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import java.util.regex.PatternSyntaxException;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.RödaValue.*;
import org.kaivos.röda.RödaStream;
import static org.kaivos.röda.RödaStream.*;
import static org.kaivos.röda.Parser.*;

import org.kaivos.nept.parser.ParsingException;

public class Interpreter {
	
	/*** INTERPRETER ***/
	
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
			else {
			        map.put(name, value);
			}
		}

		synchronized void setLocal(String name, RödaValue value) {
		        map.put(name, value);
		}
	}
	
	public RödaScope G = new RödaScope(Optional.empty());

	RödaStream STDIN, STDOUT;

	File currentDir = new File(System.getProperty("user.dir"));

	static class SystemInStream extends RödaStream {
		InputStreamReader ir = new InputStreamReader(System.in);
		BufferedReader in = new BufferedReader(ir);
		{
			inHandler = ValueStream.HANDLER;
			outHandler = ValueStream.HANDLER;
		}
		public RödaValue get() {
			try {
				while (!in.ready()) {
					if (paused()) return null;
				}
				String line = in.readLine();
				if (line == null) return null;
				else return valueFromString(line);
			} catch (IOException e) {
				error(e);
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

	static class SystemOutStream extends RödaStream {
		{
			inHandler = ValueStream.HANDLER;
			outHandler = ValueStream.HANDLER;
		}
		public RödaValue get() {
			error("no input from output");
			return null;
		}
		public void put(RödaValue val) {
			if (!closed())
				System.out.print(val.str());
			else error("output is closed");
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
	
	private void initializeIO() {
		STDIN = new SystemInStream();		
		STDOUT = new SystemOutStream();
	}

	{ Builtins.populate(this); /*System.out.println(G.map.keySet());*/ }

	static ExecutorService executor = Executors.newCachedThreadPool();

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

	@SuppressWarnings("serial")
	public static class RödaException extends RuntimeException {
		private Deque<String> stack;
		private RödaException(String message, Deque<String> stack) {
			super(message);
			this.stack = stack;
		}

		private RödaException(Throwable cause, Deque<String> stack) {
			super(cause);
			this.stack = stack;
		}

		public Deque<String> getStack() {
			return stack;
		}
	}
	
	static void error(String message) {
		RödaException e = new RödaException(message, new ArrayDeque<>(callStack.get()));
		callStack.get().clear();
		throw e;
	}

	static void error(Throwable cause) {
		RödaException e = new RödaException(cause, new ArrayDeque<>(callStack.get()));
		callStack.get().clear();
		throw e;
	}

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
			if (!main.isFunction() || main.isNativeFunction())
				error("The variable 'main' must be a function");
			
			exec("<runtime>", 0, main, args, G, STDIN, STDOUT);
		} catch (ParsingException|RödaException e) {
			throw e;
		} catch (Exception e) {
		        error(e);
		}
	}

	public static void load(String code, String filename, RödaScope scope) {
		try {
			Program program = parse(t.tokenize(code, filename));
			for (Function f : program.functions) {
				scope.setLocal(f.name, valueFromFunction(f));
			}
		} catch (ParsingException|RödaException e) {
			throw e;
		} catch (Exception e) {
		        error(e);
		}
	}

	public static void loadFile(File file, RödaScope scope) {
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
			Statement statement = parseStatement(t.tokenize(code, filename));
			evalStatement(statement, G, STDIN, STDOUT, false);
		} catch (RödaException e) {
			throw e;
		} catch (ParsingException e) {
			throw e;
		} catch (Exception e) {
		        error(e);
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
	
	static void checkListOrString(String function, RödaValue arg) {
	        if (!arg.isList() && !arg.isString()) {
			error("illegal argument for '" + function
			      + "': list or string expected (got " + arg.typeString() + ")");
		}
	}
	
	static void checkListOrNumber(String function, RödaValue arg) {
	        if (!arg.isList() && !arg.isNumber()) {
			error("illegal argument for '" + function
			      + "': list or number expected (got " + arg.typeString() + ")");
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
				if (rval.isReference()) rval = rval.resolve(true);
				args.add(rval);
			}
			else if (val.isReference()
				 && value.isFunction()
				 && isReferenceParameter(value, i)) {
				RödaValue rval = val.scope.resolve(val.target);
				if (rval != null && rval.isReference()) args.add(rval);
				else args.add(val);
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
		
		//System.err.println("exec " + value + "("+args+") " + in + " -> " + out);
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
	
	private void evalStatement(Statement statement, RödaScope scope,
				  RödaStream in, RödaStream out, boolean redirected) {
		RödaStream _in = in;
		int i = 0;
		Runnable[] runnables = new Runnable[statement.commands.size()];
		for (Command command : statement.commands) {
			boolean last = i == statement.commands.size()-1;
			RödaStream _out = last ? out : new RödaStreamImpl();
			Trair<Runnable, StreamType, StreamType> tr = evalCommand(command, scope,
										 in, out,
										 _in, _out,
										 !last || redirected);
			runnables[i] = tr.first();
		        _out.inHandler = tr.third().newHandler();
		        _in.outHandler = tr.second().newHandler();
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
			} catch (InterruptedException e) {
			        error(e);
			} catch (ExecutionException e) {
				if (e.getCause() instanceof RödaException) {
					throw (RödaException) e.getCause();
				}
				error(e.getCause());
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
	
	public Trair<Runnable, StreamType, StreamType> evalCommand(Command cmd,
								   RödaScope scope,
								   RödaStream in, RödaStream out,
								   RödaStream _in, RödaStream _out,
								   boolean canFinish) {
		if (cmd.type == Command.Type.NORMAL) {
			RödaValue function = evalExpression(cmd.name, scope, in, out);
			List<RödaValue> args = new ArrayList<>();
			for (Argument arg : cmd.arguments) {
				RödaValue value = evalExpression(arg.expr, scope, in, out, true);
				if (arg.flattened) {
					value = value.impliciteResolve();
					checkList("*", value);
					args.addAll(value.list);
				}
				else args.add(value);
			}
			Runnable r = () -> {
				exec(cmd.file, cmd.line, function, args, scope, _in, _out);
				if (canFinish) _out.finish();
			};
			StreamType ins, outs;
			if (function.isFunction() && !function.isNativeFunction()) {
				ins = function.function.input;
				outs = function.function.output;
			} else if (function.isNativeFunction()) {
				ins = function.nfunction.input;
				outs = function.nfunction.output;
			} else {
				ins = new ValueStream();
				outs = new ValueStream();
			}
			return new Trair<>(r, ins, outs);
		}

		if (cmd.type == Command.Type.VARIABLE) {
			List<RödaValue> args = new ArrayList<>();
			for (Argument arg : cmd.arguments) {
				RödaValue value = evalExpression(arg.expr, scope, in, out, true)
					.impliciteResolve();
				if (arg.flattened) {
					checkList("*", value);
					args.addAll(value.list);
				}
				else args.add(value);
			}
			Expression e = cmd.name;
			if (e.type != Expression.Type.VARIABLE && e.type != Expression.Type.ELEMENT)
				error("bad lvalue for '" + cmd.operator + "': " + e.asString());
			Consumer<RödaValue> assign, assignLocal;
			if (e.type == Expression.Type.VARIABLE) {
				assign = v -> {
				        RödaValue value = scope.resolve(e.variable);
					if (value == null || !value.isReference())
						value = valueFromReference(scope, e.variable);
					value.assign(v);
				};
				assignLocal = v -> {
				        RödaValue value = scope.resolve(e.variable);
					if (value == null || !value.isReference())
						value = valueFromReference(scope, e.variable);
					value.assignLocal(v);
				};
			}
		        else {
				assign = v -> {
					RödaValue list = evalExpression(e.sub, scope, in, out).impliciteResolve();
					int index = evalExpression(e.index, scope, in, out)
					.impliciteResolve().num();
					if (index < 0) index = list.list.size()+index;
					if (list.list.size() <= index)
						error("array index out of bounds: index " + index
						      + ", size " + list.list.size());
					list.list.set(index, v);
				};
				assignLocal = assign;
			}
			Supplier<RödaValue> resolve = () -> evalExpression(e, scope, in, out).impliciteResolve();
			Runnable r;
			switch (cmd.operator) {
			case ":=": {
				r = () -> {
					if (args.size() > 1) argumentUnderflow(":=", 1, args.size());
					assignLocal.accept(args.get(0));
				};
			} break;
			case "=": {
				r = () -> {
					if (args.size() > 1) argumentUnderflow("=", 1, args.size());
					assign.accept(args.get(0));
				};
			} break;
			case "++": {
				r = () -> {
					RödaValue v = resolve.get();
					checkNumber("++", v);
					assign.accept(valueFromInt(v.num()+1));
				};
			} break;
			case "--": {
				r = () -> {
					RödaValue v = resolve.get();
					checkNumber("--", v);
					assign.accept(valueFromInt(v.num()-1));
				};
			} break;
			case "+=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkListOrNumber("+=", v);
					if (v.isList()) {
						v.list.add(args.get(0));
					}
					else {
						checkNumber("+=", args.get(0));
						assign.accept(valueFromInt(v.num()+args.get(0).num()));
					}
				};
			} break;
			case "-=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkNumber("-=", v);
					checkNumber("-=", args.get(0));
					assign.accept(valueFromInt(v.num()-args.get(0).num()));
				};
			} break;
			case "*=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkNumber("*=", v);
					checkNumber("*=", args.get(0));
					assign.accept(valueFromInt(v.num()*args.get(0).num()));
				};
			} break;
			case "/=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkNumber("/=", v);
					checkNumber("/=", args.get(0));
					assign.accept(valueFromInt(v.num()/args.get(0).num()));
				};
			} break;
			case ".=": {
				r = () -> {
					RödaValue v = resolve.get();
					checkListOrString(".=", v);
					if (v.isList()) {
						checkList(".=", args.get(0));
						ArrayList<RödaValue> newList = new ArrayList<>();
						newList.addAll(v.list);
						newList.addAll(args.get(0).list);
						assign.accept(valueFromList(newList));
					}
					else {
						checkString(".=", args.get(0));
						assign.accept(valueFromString(v.str()+args.get(0).str()));
					}
				};
			} break;
			case "~=": {
				r = () -> {
					RödaValue rval = resolve.get();
					checkString(".=", rval);
					if (args.size() % 2 != 0) error("invalid arguments for '~=': even number required (got " + (args.size()-1) + ")");
					String text = rval.str();
					try {
						for (int j = 0; j < args.size(); j+=2) {
							checkString(e.asString() + "~=", args.get(j));
							checkString(e.asString() + "~=", args.get(j+1));
							String pattern = args.get(j).str();
							String replacement = args.get(j+1).str();
							text = text.replaceAll(pattern, replacement);
						}
					} catch (PatternSyntaxException ex) {
						error("'"+e.asString()+"~=': pattern syntax exception: "
						      + ex.getMessage());
					}
					assign.accept(valueFromString(text));
					return;
				};
			} break;
			case "?": {
				r = () -> {
					if (e.type != Expression.Type.VARIABLE)
						error("bad lvalue for '?': " + e.asString());
					_out.push(valueFromBoolean(scope.resolve(e.variable) != null));
				};
			} break;
			default:
				error("unknown operator " + cmd.operator);
				r = null;
			}
			Runnable finalR = () -> {
				callStack.get().push("variable command " + e.asString() + " " + cmd.operator + " "
						     + args.stream().map(RödaValue::str).collect(joining(" "))
						     + "\n\tat " + cmd.file + ":" + cmd.line);
				r.run();
				callStack.get().pop();
			};
			return new Trair<>(finalR, new ValueStream(), new ValueStream());
		}
		
		if (cmd.type == Command.Type.WHILE || cmd.type == Command.Type.IF) {
			boolean isWhile = cmd.type == Command.Type.WHILE;
			Runnable r = () -> {
				RödaScope newScope = new RödaScope(scope);
				boolean goToElse = true;
				do {
					RödaStream condOut = makeStream(new ValueStream(), new BooleanStream());
					evalStatement(cmd.cond, scope, _in, condOut, true);
					if (!condOut.pull().bool()) break;
					goToElse = false;
					for (Statement s : cmd.body) {
						evalStatement(s, newScope, _in, _out, false);
					}
				} while (isWhile);
				if (goToElse && cmd.elseBody != null) {
					for (Statement s : cmd.elseBody) {
						evalStatement(s, newScope, _in, _out, false);
					}
				}
				if (canFinish) _out.finish();
			};
			return new Trair<>(r, new ValueStream(), new ValueStream());
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
			return new Trair<>(r, new ValueStream(), new ValueStream());
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
			return new Trair<>(r, new ValueStream(), new ValueStream());
		}

		if (cmd.type == Command.Type.TRY) {
			Runnable r = () -> {
				try {
				        evalCommand(cmd.cmd, scope, in, out, _in, _out, false);
				} catch (Exception e) {} // virheet ohitetaan TODO virheenkäsittely
				if (canFinish) _out.finish();
			};
			return new Trair<>(r, new ValueStream(), new ValueStream());
		}


		error("unknown command");
		return null;
	}
	
	private RödaValue evalExpression(Expression exp, RödaScope scope, RödaStream in, RödaStream out) {
		return evalExpressionWithoutErrorHandling(exp, scope, in, out, false);
	}
	
	private RödaValue evalExpression(Expression exp, RödaScope scope, RödaStream in, RödaStream out,
					 boolean variablesAreReferences) {
		callStack.get().push("expression " + exp.type + "\n\tat " + exp.file + ":" + exp.line);
		RödaValue value = evalExpressionWithoutErrorHandling(exp, scope, in, out,
								     variablesAreReferences);
		callStack.get().pop();
		return value;
	}
	
	private RödaValue evalExpressionWithoutErrorHandling(Expression exp, RödaScope scope,
							     RödaStream in, RödaStream out,
							     boolean variablesAreReferences) {
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
			RödaValue val = _out.readAll();
			if (val == null)
				error("empty stream");
			return val;
		}
		if (exp.type == Expression.Type.VARIABLE) {
			if (variablesAreReferences) {
				return valueFromReference(scope, exp.variable);
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
					if (!sub.isBoolean()) error("tried to NOT a " + sub.typeString());
					return valueFromBoolean(!sub.bool());
				case NEG:
					if (!sub.isNumber()) error("tried to NEG a " + sub.typeString());
					return valueFromInt(-sub.num());
				case BNOT:
					if (!sub.isNumber()) error("tried to BNOT a " + sub.typeString());
					return valueFromInt(~sub.num());
				}
			}
			else {
				RödaValue val1 = evalExpression(exp.exprA, scope, in, out).impliciteResolve();
				RödaValue val2 = evalExpression(exp.exprB, scope, in, out).impliciteResolve();
				switch (exp.ctype) {
				case AND:
					if (!val1.isBoolean()) error("tried to AND a " + val1.typeString());
					if (!val2.isBoolean()) error("tried to AND a " + val2.typeString());
					return valueFromBoolean(val1.bool() && val2.bool());
				case OR:
					if (!val1.isBoolean()) error("tried to OR a " + val1.typeString());
					if (!val2.isBoolean()) error("tried to OR a " + val2.typeString());
					return valueFromBoolean(val1.bool() || val2.bool());
				case XOR:
					if (!val1.isBoolean()) error("tried to XOR a " + val1.typeString());
					if (!val2.isBoolean()) error("tried to XOR a " + val2.typeString());
					return valueFromBoolean(val1.bool() ^ val2.bool());
				case EQ:
					return valueFromBoolean(val1.halfEq(val2));
				case NEQ:
					return valueFromBoolean(!val1.halfEq(val2));
				}
				if (!val1.isNumber()) error("tried to " + exp.ctype + " a " + val1.typeString());
				if (!val2.isNumber()) error("tried to " + exp.ctype + " a " + val2.typeString());
				switch (exp.ctype) {
				case MUL:
					return valueFromInt(val1.num()*val2.num());
				case DIV:
					return valueFromInt(val1.num()/val2.num());
				case ADD:
					return valueFromInt(val1.num()+val2.num());
				case SUB:
					return valueFromInt(val1.num()-val2.num());
				case BAND:
					return valueFromInt(val1.num()&val2.num());
				case BOR:
					return valueFromInt(val1.num()|val2.num());
				case BXOR:
					return valueFromInt(val1.num()^val2.num());
				case BLSHIFT:
					return valueFromInt(val1.num()<<val2.num());
				case BRSHIFT:
					return valueFromInt(val1.num()>>val2.num());
				case BRRSHIFT:
					return valueFromInt(val1.num()>>>val2.num());
				case LT:
					return valueFromBoolean(val1.num()<val2.num());
				case GT:
					return valueFromBoolean(val1.num()>val2.num());
				case LE:
					return valueFromBoolean(val1.num()<=val2.num());
				case GE:
					return valueFromBoolean(val1.num()>=val2.num());
				}
			}
			error("unknown expression type " + exp.ctype);
			return null;
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
