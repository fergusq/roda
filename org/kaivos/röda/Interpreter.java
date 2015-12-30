package org.kaivos.röda;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Stack;
import java.util.Iterator;
import java.util.ListIterator;

import java.util.Random;
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

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.File;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.kaivos.röda.Calculator;
import org.kaivos.röda.IOUtils;
import org.kaivos.röda.JSON;
import org.kaivos.röda.JSON.JSONElement;
import org.kaivos.röda.JSON.JSONInteger;
import org.kaivos.röda.JSON.JSONString;
import org.kaivos.röda.JSON.JSONList;
import org.kaivos.röda.JSON.JSONMap;

import static org.kaivos.röda.Parser.*;

public class Interpreter {
	
	/*** INTERPRETER ***/

	public static class RödaValue {
		enum Type {
			FUNCTION,
			NATIVE_FUNCTION,
			STRING,
			NUMBER,
			BOOLEAN,
			REFERENCE,
			LIST
		}

		Type type;

		// STRING
		String text;

		// INT
		int number;

		// BOOLEAN
		boolean bool;

		// FUNCTION
		Function function;

		// NATIVE_FUNCTION
		NativeFunction nfunction;

		// REFERENCE
		String target;
		RödaScope scope;

		// LIST
		List<RödaValue> list;
		
		RödaValue() {} // käytä apufunktioita

		RödaValue copy() {
			RödaValue val = new RödaValue();
			val.type = type;
			val.text = text;
			val.number = number;
			val.bool = bool;
			val.function = function;
			val.nfunction = nfunction;
			val.target = target;
			val.scope = scope;
			if (list != null) {
				val.list = new ArrayList<>(list.size());
				for (RödaValue item : list) val.list.add(item);
			} else val.list = null;
			return val;
		}

		String str() {
			if (type == Type.BOOLEAN) return bool ? "true" : "false";
			if (type == Type.STRING) return text;
			if (type == Type.NUMBER) return ""+number;
			if (type == Type.FUNCTION) return "<function '"+function.name+"'>";
			if (type == Type.NATIVE_FUNCTION) return "<function '"+nfunction.name+"'>";
			if (type == Type.REFERENCE) {
				return "&" + target;
			}
			if (type == Type.LIST) {
				return "(" + list.stream().map(RödaValue::str).collect(joining(" ")) + ")";
			}
			error("unknown type " + type);
			return null;
		}

		boolean bool() {
			if (type == Type.BOOLEAN) return bool;
			if (type == Type.REFERENCE) return scope.resolve(target).bool();
			return true;
		}

		int num() {
			if (type == Type.NUMBER) return number;
			if (type == Type.STRING) {
				try {
					return Integer.parseInt(text);
				} catch (NumberFormatException e) {
					error("can't convert '" + text + "' to a number");
				}
			}
			error("can't convert '" + str() + "' to a number");
			return -1;
		}

		RödaValue resolve(boolean implicite) {
			if (type == Type.REFERENCE) {
				RödaValue t = scope.resolve(target);
				if (t == null) error("variable not found (via " + (implicite ? "implicite" : "explicite") + " reference): " + target);
				return t;
			}
			error("can't dereference a " + type);
			return null;
		}

		RödaValue impliciteResolve() {
			if (isReference()) return resolve(true);
			return this;
		}

		void assign(RödaValue value) {
			if (type == Type.REFERENCE) {
				scope.set(target, value);
				return;
			}
			error("can't assign a " + type);
		}

		void assignLocal(RödaValue value) {
			if (type == Type.REFERENCE) {
				scope.setLocal(target, value);
				return;
			}
			error("can't assign a " + type);
		}

		boolean weakEq(RödaValue value) {
			return str().equals(value.str());
		}

		/** Viittauksien vertaileminen kielletty **/
		boolean halfEq(RödaValue value) {
			if (type == Type.STRING && value.type == Type.NUMBER
			    || type == Type.NUMBER && value.type == Type.STRING) {
				return weakEq(value);
			}
			else return strongEq(value);
		}
		
		/** Viittauksien vertaileminen kielletty **/
		boolean strongEq(RödaValue value) {
			if (type != value.type) return false;
			switch (type) {
			case STRING:
				return text.equals(value.text);
			case NUMBER:
				return number == value.number;
			case BOOLEAN:
				return bool == value.bool;
			case FUNCTION:
				return function == value.function;
			case NATIVE_FUNCTION:
				return nfunction == value.nfunction;
			case LIST:
				boolean ans = true;
				for (int i = 0; i < list.size(); i++)
					ans &= list.get(i).strongEq(value.list.get(i));
				return ans;
			case REFERENCE:
				// tätä ei oikeasti pitäisi koskaan tapahtua
				return false;
			default:
				// eikä tätä
				return false;
			}
		}

		boolean isFunction() {
			return type == Type.FUNCTION || type == Type.NATIVE_FUNCTION;
		}

		boolean isList() {
			return type == Type.LIST;
		}

		boolean isString() {
			return type == Type.STRING || type == Type.NUMBER;
		}
		
		boolean isReference() {
			return type == Type.REFERENCE;
		}

		@Override
		public String toString() {
			return "RödaValue{str=" + str() + "}";
		}
	}

	private static class NativeFunction {
		String name;
		NativeFunctionBody body;
		boolean isVarargs;
		List<Parameter> parameters;
	}
	
	private static interface NativeFunctionBody {
		public void exec(List<RödaValue> rawArgs, List<RödaValue> args, RödaScope scope,
				 RödaStream in, RödaStream out);
	}

	public static RödaValue valueFromString(String text) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.STRING;
		val.text = text;
		return val;
	}

	public static RödaValue valueFromInt(int number) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.NUMBER;
		val.number = number;
		return val;
	}

	public static RödaValue valueFromBoolean(boolean bool) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.BOOLEAN;
		val.bool = bool;
		return val;
	}

	public static RödaValue valueFromList(List<RödaValue> list) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.LIST;
		val.list = list;
		return val;
	}

	public static RödaValue valueFromList(RödaValue... elements) {
		return valueFromList(new ArrayList<>(Arrays.asList(elements)));
	}
	
	public static RödaValue valueFromFunction(Function function) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.FUNCTION;
		val.function = function;
		return val;
	}
	
	public static RödaValue valueFromFunction(Function function, RödaScope localScope) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.FUNCTION;
		val.function = function;
		val.scope = localScope;
		return val;
	}

	public static RödaValue valueFromNativeFunction(String name, NativeFunctionBody body, List<Parameter> parameters, boolean isVarargs) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.NATIVE_FUNCTION;
		NativeFunction function = new NativeFunction();
		function.name = name;
		function.body = body;
		function.isVarargs = isVarargs;
		function.parameters = parameters;
		val.nfunction = function;
		return val;
	}

	public static RödaValue valueFromReference(RödaScope scope, String name) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.REFERENCE;
		val.scope = scope;
		val.target = name;
		return val;
	}

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
					if (value.type != RödaValue.Type.STRING) {
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
						if (val.type != RödaValue.Type.STRING) {
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
					if (value.type != RödaValue.Type.STRING) {
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
						if (val.type != RödaValue.Type.STRING) return val;
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
					if (value.type == RödaValue.Type.BOOLEAN && !value.bool) return value;
					return valueFromBoolean(true);
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

	private void initialize() {
		G.setLocal("push", valueFromNativeFunction("push", (rawArgs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("push", 1, 0);
						return;
					}
					for (RödaValue value : args) {
						out.push(value);
					}
				}, Arrays.asList(new Parameter("values", false)), true));

		G.setLocal("pull", valueFromNativeFunction("pull", (rawArgs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("pull", 1, 0);
						return;
					}
					boolean readMode = false;
					for (RödaValue value : rawArgs) {
						if (value.type == RödaValue.Type.STRING && value.text.equals("-r")) {
							readMode = true;
							continue;
						}
						else if (value.type != RödaValue.Type.REFERENCE)
							error("invalid argument for pull: only references accepted");
						
						//System.err.print(value.target);
						if (in.finished()) {
							//System.err.println("->false");
							if (readMode) out.push(valueFromBoolean(false));
							continue;
						}
						RödaValue pulled = in.pull();
						if (pulled == null) {
							if (readMode) out.push(valueFromBoolean(false));
							continue;
						}
						value.scope.set(value.target, pulled);
						//System.err.println("="+pulled);
						if (readMode) out.push(valueFromBoolean(true));
					}
				}, Arrays.asList(new Parameter("variables", true)), true));

		G.setLocal("seq", valueFromNativeFunction("seq", (rawArgs, args, scope, in, out) -> {
					checkNumber("seq", args.get(0));
					checkNumber("seq", args.get(1));
					int from = (int) args.get(0).num();
					int to = (int) args.get(1).num();
					for (int i = from; i <= to; i++) out.push(valueFromInt(i));
				}, Arrays.asList(new Parameter("from", false), new Parameter("to", false)), false));

		G.setLocal("grep", valueFromNativeFunction("grep", (rawArgs, args, scope, in, out) -> {
					if (args.size() < 1) argumentUnderflow("grep", 1, 0);
					checkString("grep", args.get(0));
					boolean onlyMatching = args.get(0).str().equals("-o");
					
					// basic mode
					if (!onlyMatching) {
						for (RödaValue input : in) {
							String text = input.str();
							for (RödaValue value : args) {
								checkString("grep", value);
								String pattern = value.str();
								if (text.matches(pattern))
									out.push(input);
							}
						}
					}

					// only matching mode
					if (onlyMatching) {
						args.remove(0);
						for (RödaValue input : in) {
							String text = input.str();
							for (RödaValue value : args) {
								checkString("grep", value);
								Pattern pattern = Pattern.compile(value.str());
								Matcher m = pattern.matcher(text);
								while (m.find()) {
									out.push(valueFromString(m.group()));
								}
							}
						}
					}
				}, Arrays.asList(new Parameter("patterns", false)), true));

		G.setLocal("replace", valueFromNativeFunction("replace", (rawArgs, args, scope, in, out) -> {
					if (args.size() % 2 != 0) error("invalid arguments for replace: even number required (got " + args.size() + ")");
					try {
						for (RödaValue input : in) {
							String text = input.str();
							for (int i = 0; i < args.size(); i+=2) {
								checkString("replace", args.get(i));
								checkString("replace", args.get(i+1));
								String pattern = args.get(i).str();
								String replacement = args.get(i+1).str();
								text = text.replaceAll(pattern, replacement);
							}
							out.push(valueFromString(text));
						}
					} catch (PatternSyntaxException e) {
						error("replace: pattern syntax error: " + e.getMessage());
					}
				}, Arrays.asList(new Parameter("patterns_and_replacements", false)), true));

		G.setLocal("split", valueFromNativeFunction("split", (rawArgs, args, scope, in, out) -> {
					String separator = " ";
					for (int i = 0; i < args.size(); i++) {
						RödaValue value = args.get(i);
						checkString("split", value);
						String str = value.str();
						if (str.equals("-s")) {
							RödaValue newSep = args.get(++i);
							checkString("split", newSep);
							separator = newSep.str();
							continue;
						}
						for (String s : str.split(separator)) {
							out.push(valueFromString(s));
						}
					}
				}, Arrays.asList(new Parameter("flags_and_strings", false)), true));

		G.setLocal("json", valueFromNativeFunction("json", (rawArgs, args, scope, in, out) -> {
					boolean _stringOutput = false;
					boolean _iterativeOutput = false;
					while (args.size() > 0
					       && args.get(0).isString()
					       && args.get(0).str().startsWith("-")) {
						String flag = args.get(0).str();
						if (flag.equals("-s")) _stringOutput = true;
						if (flag.equals("-i")) _iterativeOutput = true;
						else error("json: unknown option " + flag);
						args.remove(0);
					}
					boolean stringOutput = _stringOutput;
					boolean iterativeOutput = _iterativeOutput;
					Consumer<String> handler = code -> {
						JSONElement root = JSON.parseJSON(code);
						if (iterativeOutput) {
							for (JSONElement element : root) {
								if (!stringOutput) {
									RödaValue path = valueFromList(element.getPath().stream()
												       .map(jk -> valueFromString(jk.toString()) )
												       .collect(toList()));
									RödaValue value = valueFromString(element.toString());
									out.push(valueFromList(path, value));
								} else {
									out.push(valueFromString(element.getPath().toString() + " " + element.toString()));
								}
							}
						} else { // rekursiivinen ulostulo
							// apuluokka rekursion mahdollistamiseksi
							class R<I> { I i; }
							R<java.util.function.Function<JSONElement, RödaValue>>
							makeRöda = new R<>();
							makeRöda.i = json -> {
								RödaValue elementName = valueFromString(json.getElementName());
								RödaValue value;
								if (json instanceof JSONInteger) {
									value = valueFromInt(((JSONInteger) json).getValue());
								}
								else if (json instanceof JSONString) {
									value = valueFromString(((JSONString) json).getValue());
								}
								else if (json instanceof JSONList) {
									value = valueFromList(((JSONList) json).getElements()
											      .stream()
											      .map(j -> makeRöda.i.apply(j))
											      .collect(toList()));
								}
								else if (json instanceof JSONMap) {
									value = valueFromList(((JSONMap) json).getElements().entrySet()
											      .stream()
											      .map(e -> valueFromList(valueFromString(e.getKey().getKey()),
														      makeRöda.i.apply(e.getValue())))
											      .collect(toList()));
								}
								else {
									value = valueFromString(json.toString());
								}
								return valueFromList(elementName, value);
							};
							out.push(makeRöda.i.apply(root));
							
						}
					};
					if (args.size() > 1) argumentOverflow("json", 1, args.size());
					else if (args.size() == 1) {
						RödaValue arg = args.get(0);
						checkString("json", arg);
						String code = arg.str();
						handler.accept(code);
					}
					else {
						for (RödaValue value : in) {
							String code = value.str();
							handler.accept(code);
						}
					}
				}, Arrays.asList(new Parameter("flags_and_code", false)), true));

		G.setLocal("list", valueFromNativeFunction("list", (rawArgs, args, scope, in, out) -> {
				        out.push(valueFromList(args));
				}, Arrays.asList(new Parameter("values", false)), true));

		G.setLocal("true", valueFromNativeFunction("list", (rawArgs, args, scope, in, out) -> {
				        out.push(valueFromBoolean(true));
				}, Arrays.asList(), false));

		G.setLocal("false", valueFromNativeFunction("false", (rawArgs, args, scope, in, out) -> {
				        out.push(valueFromBoolean(false));
				}, Arrays.asList(), false));

		Random rnd = new Random();
		
		G.setLocal("random", valueFromNativeFunction("random", (rawArgs, args, scope, in, out) -> {
					final int INTEGER=0,
						FLOAT=1,
						BOOLEAN=2;
					java.util.function.Function<Integer, RödaValue> next = i -> {
						switch (i) {
						case INTEGER: return valueFromInt(rnd.nextInt());
						case FLOAT: return valueFromString(rnd.nextDouble()+"");
						case BOOLEAN: return valueFromBoolean(rnd.nextBoolean());
						}
						return null;
					};
					int mode = BOOLEAN;
					if (args.size() == 1 && args.get(0).type == RödaValue.Type.STRING) {
						switch (args.get(0).str()) {
						case "-integer": mode = INTEGER; break;
						case "-boolean": mode = BOOLEAN; break;
						case "-float": mode = FLOAT; break;
						default: error("random: invalid flag " + args.get(0).str());
						}
						args.remove(0);
					}
					if (args.size() == 0) {
						out.push(next.apply(mode));
						return;
					}
					for (RödaValue variable : args) {
						checkReference("random", variable);
						variable.assign(next.apply(mode));
					}
				}, Arrays.asList(new Parameter("flags_and_variables", true)), true));
		
		G.setLocal("expr", valueFromNativeFunction("expr", (rawArgs, args, scope, in, out) -> {
				        String expression = args.stream().map(RödaValue::str).collect(joining(" "));
					out.push(valueFromInt(Calculator.eval(expression)));
				}, Arrays.asList(new Parameter("expressions", false)), true));

		G.setLocal("test", valueFromNativeFunction("test", (rawArgs, args, scope, in, out) -> {
					checkString("test", args.get(1));
					String operator = args.get(1).str();
					boolean not = false;
					if (operator.startsWith("-not-")) {
						operator = operator.substring(4);
						not = true;
					}
					RödaValue a1 = args.get(0);
					RödaValue a2 = args.get(2);
					switch (operator) {
					case "-eq": {
						out.push(valueFromBoolean(a1.halfEq(a2)^not));
					} break;
					case "-strong_eq": {
						out.push(valueFromBoolean(a1.strongEq(a2)^not));
					} break;
					case "-weak_eq": {
						out.push(valueFromBoolean(a1.weakEq(a2)^not));
					} break;
					case "-matches": {
						checkString("test -matches", a1);
						checkString("test -matches", a2);
						out.push(valueFromBoolean(a1.str().matches(a2.str())^not));
					} break;
					case "-lt": {
						checkNumber("test -lt", a1);
						checkNumber("test -lt", a2);
						out.push(valueFromBoolean((a1.num() < a2.num())^not));
					} break;
					case "-le": {
						checkNumber("test -le", a1);
						checkNumber("test -le", a2);
						out.push(valueFromBoolean((a1.num() <= a2.num())^not));
					} break;
					case "-gt": {
						checkNumber("test -gt", a1);
						checkNumber("test -gt", a2);
						out.push(valueFromBoolean((a1.num() > a2.num())^not));
					} break;
					case "-ge": {
						checkNumber("test -ge", a1);
						checkNumber("test -ge", a2);
						out.push(valueFromBoolean((a1.num() >= a2.num())^not));
					} break;
					default:
						error("test: unknown operator '" + operator + "'");
					}
				}, Arrays.asList(
						 new Parameter("value1", false),
						 new Parameter("operator", false),
						 new Parameter("value2", false)
						 ), false));

		G.setLocal("head", valueFromNativeFunction("head", (rawArgs, args, scope, in, out) -> {
				        if (args.size() > 1) argumentOverflow("head", 1, args.size());
					if (args.size() == 0) {
						out.push(in.pull());
					}
					else {
						checkNumber("head", args.get(0));
						int num = (int) args.get(0).num();
						for (int i = 0; i < num; i++) out.push(in.pull());
					}
				}, Arrays.asList(new Parameter("number", false)), true));

		G.setLocal("tail", valueFromNativeFunction("tail", (rawArgs, args, scope, in, out) -> {
				        if (args.size() > 1) argumentOverflow("tail", 1, args.size());

					int num;

					if (args.size() == 0) num = 1;
					else {
						checkNumber("tail", args.get(0));
						num = (int) args.get(0).num();
					}
					
					List<RödaValue> values = new ArrayList<>();
					for (RödaValue value : in) {
						values.add(value);
					}

					for (int i = values.size()-num; i < values.size(); i++) {
						out.push(values.get(i));
					}
					
				}, Arrays.asList(new Parameter("number", false)), true));

		G.setLocal("write", valueFromNativeFunction("write", (rawArgs, args, scope, in, out) -> {
					checkString("write", args.get(0));
					String filename = args.get(0).str();
					try {
						PrintWriter writer = new PrintWriter(filename);
					        for (RödaValue input : in) {
							writer.print(input.str());
						}
						writer.close();
					} catch (IOException e) {
						e.printStackTrace();
						error("write: io error");
					}
				}, Arrays.asList(new Parameter("file", false)), false));

		G.setLocal("cat", valueFromNativeFunction("cat", (rawArgs, args, scope, in, out) -> {
					if (args.size() == 0) {
					        for (RödaValue input : in) {
							out.push(input);
						}
					}
					else for (RödaValue file : args) {
							checkString("cat", file);
							String filename = file.str();
							for (String line : IOUtils.fileIterator(filename)) {
								out.push(valueFromString(line));
							}
						}
				}, Arrays.asList(new Parameter("files", false)), true));

		G.setLocal("wcat", valueFromNativeFunction("wcat", (rawArgs, args, scope, in, out) -> {
					if (args.size() == 0) {
						for (RödaValue input : in) {
							out.push(input);
						}
					}
					else try {
							String useragent = "";
							String outputFile = "";
							for (int i = 0; i < args.size(); i++) {
								RödaValue _arg = args.get(i);
								checkString("wcat", _arg);
								String arg = _arg.str();

								if (arg.equals("-U")) {
									RödaValue _ua = args.get(++i);
									checkString("wcat", _ua);
									useragent = _ua.str();
									continue;
								}
								if (arg.equals("-O")) {
									RödaValue _of = args.get(++i);
									checkString("wcat", _of);
									outputFile = _of.str();
									continue;
								}
								URL url = new URL(arg);
								URLConnection c = url.openConnection();
								if (!useragent.isEmpty())
									c.setRequestProperty("User-Agent" , useragent);
								c.connect();
								InputStream input = c.getInputStream();
								if (!outputFile.isEmpty()) {
									Files.copy(input, new File(outputFile).toPath(), StandardCopyOption.REPLACE_EXISTING);
								}
								else {
									for (String line : IOUtils.streamIterator(input)) {
										out.push(valueFromString(line));
									}
								}
								input.close();
							}
						} catch (MalformedURLException e) {
							e.printStackTrace();
							error("wcat: malformed url" + e.getMessage());
						} catch (IOException e) {
							e.printStackTrace();
							error("wcat: io error");
						}
				}, Arrays.asList(new Parameter("urls", false)), true));
		
		G.setLocal("exec", valueFromNativeFunction("exec", (rawArgs, args, scope, in, out) -> {
					List<String> params = args.stream().map(v -> v.str()).collect(toList());
					try {

						ProcessBuilder b = new ProcessBuilder(params);
						Process p = b.start();
						InputStream pout = p.getInputStream();
						PrintWriter pin = new PrintWriter(p.getOutputStream());
						Runnable input = () -> {
							for (RödaValue value : in) {
								pin.print(value.str());
							}
							pin.close();
						};
						Runnable output = () -> {
							try {
								for (String line : IOUtils.streamIterator(pout)) {
									out.push(valueFromString(line));
								}
								pout.close();
							} catch (IOException e) {
								e.printStackTrace();
								error("io error while executing '" + params.stream().collect(joining(" ")) + "'");
							}
						};
						new Thread(input).start();
						Thread o = new Thread(output);
						o.start();
						o.join();
					} catch (IOException e) {
						e.printStackTrace();
						error("exec: io error while executing '" + params.stream().collect(joining(" ")) + "'");
					} catch (InterruptedException e) {
						e.printStackTrace();
						error("exec: threading error while executing '" + params.stream().collect(joining(" ")) + "'");
					}
				}, Arrays.asList(new Parameter("command", false), new Parameter("args", false)), true));
	}

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

	{ initialize(); initializeIO(); /*System.out.println(G.map.keySet());*/ }

	public static ThreadLocal<Stack<String>> callStack = new InheritableThreadLocal<Stack<String>>() {
			@Override protected Stack<String> childValue(Stack<String> parentValue) {
				return (Stack<String>) parentValue.clone();
			}
		};

	static { callStack.set(new Stack<>()); }

	private static class FatalException extends RuntimeException {
		private FatalException(String message) {
			super(message);
		}
	}
	
	private static void error(String message) {
		System.err.println("FATAL ERROR: " + message);
		printStackTrace();
		throw new FatalException(message);
	}

	private static void printStackTrace() {
		Stack<String> stack = callStack.get();
		ListIterator iterator = stack.listIterator(stack.size());
		while (iterator.hasPrevious()) {
			System.err.println(iterator.previous());
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
		} catch (Exception e) {
			e.printStackTrace();
		        printStackTrace();
		} 
	}
	
	private boolean isReferenceParameter(RödaValue function, int i) {
		assert function.isFunction();
		boolean isNative = function.type == RödaValue.Type.NATIVE_FUNCTION;
		List<Parameter> parameters = isNative ? function.nfunction.parameters : function.function.parameters;
		boolean isVarargs = isNative ? function.nfunction.isVarargs : function.function.isVarargs;
		if (isVarargs && i >= parameters.size()-1) return parameters.get(parameters.size()-1).reference;
		else if (i >= parameters.size()) return false; // tästä tulee virhe myöhemmin
		else return parameters.get(i).reference;
	}

	private void checkReference(String function, RödaValue arg) {
		switch (arg.type) {
		case REFERENCE:
			return;
		default:
			error("illegal argument for '" + function + "': reference expected (got " + arg.type + ")");
		}
	}
	
	private void checkList(String function, RödaValue arg) {
		switch (arg.type) {
		case LIST:
			return;
		default:
			error("illegal argument for '" + function + "': list expected (got " + arg.type + ")");
		}
	}
	
	private void checkString(String function, RödaValue arg) {
		switch (arg.type) {
		case STRING:
		case NUMBER:
			return;
		default:
			error("illegal argument for '" + function + "': string expected (got " + arg.type + ")");
		}
	}

	private void checkNumber(String function, RödaValue arg) {
		switch (arg.type) {
		case NUMBER:
			return;
		case STRING:
			try { Integer.parseInt(arg.text); return; }
			catch (NumberFormatException e) {}
		default:
			error("illegal argument for '" + function + "': number expected (got " + arg.type + ")");
		}
	}
	
	private void checkArgs(String function, int required, int got) {
		if (got > required) argumentOverflow(function, required, got);
		if (got < required) argumentUnderflow(function, required, got);
	}
	
	private void argumentOverflow(String function, int required, int got) {
		error("illegal number of arguments for '" + function + "': at most " + required + " required (got " + got + ")");
	}

	private void argumentUnderflow(String function, int required, int got) {
		error("illegal number of arguments for '" + function + "': at least " + required + " required (got " + got + ")");
	}

	private void exec(String file, int line,
			  RödaValue value, List<RödaValue> rawArgs, RödaScope scope,
			  RödaStream in, RödaStream out) {
		List<RödaValue> args = new ArrayList<>();
		int i = 0;
		for (RödaValue val : rawArgs) {
			if (val.type == RödaValue.Type.REFERENCE
			    && !(value.isFunction()
				&& isReferenceParameter(value, i))) {
				RödaValue rval = val.resolve(true);
				if (rval == null) error("variable not found (via implicite reference): " + val.target);
				args.add(rval);
			}
			else if (val.type == RödaValue.Type.LIST) {
				args.add(val.copy());
			}
			else args.add(val);
			i++;
		}
		
		callStack.get().push("calling " + value.str()
				     + " with arguments " + args.stream().map(RödaValue::str).collect(joining(", "))
				     + "\n\tat " + file + ":" + line);
		execWithoutErrorHandling(value, rawArgs, args, scope, in, out);
		callStack.get().pop();
	}
	
	public void execWithoutErrorHandling(RödaValue value, List<RödaValue> rawArgs, List<RödaValue> args,
					     RödaScope scope, RödaStream in, RödaStream out) {
		
		//callStack.push("exec " + value + "("+args+") " + in + " -> " + out);
		if (value.type == RödaValue.Type.REFERENCE) {
			if (args.isEmpty()) {
				RödaValue rval = value.resolve(false);
				if (rval == null) error("variable not found (via explicite reference): " + value.target);
				if (rval.type == RödaValue.Type.LIST)
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
					int index = (int) args.get(1).num();
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
		if (value.type == RödaValue.Type.LIST) {
			for (RödaValue item : value.list)
				out.push(item);
			return;
		}
		if (value.type == RödaValue.Type.FUNCTION) {
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
		if (value.type == RödaValue.Type.NATIVE_FUNCTION) {
			if (!value.nfunction.isVarargs) {
				checkArgs(value.nfunction.name, value.nfunction.parameters.size(), args.size());
			}
			value.nfunction.body.exec(rawArgs, args, scope, in, out);
			return;
		}
		error("can't execute a value of type " + value.type.toString());
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
			Future<Void> last = null;
			for (Runnable r : runnables) {
				boolean first = i++ == 0;
				Future<Void> previous = last;
			        Callable<Void> task = () -> {
					if (!first) {
						try {
							previous.get();
						} catch (InterruptedException|ExecutionException e) {
							e.printStackTrace();
							error("java exception");
						}
					}
					r.run();
					return null;
				};
				last = executor.submit(task);
			}
			try {
				last.get();
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
				if (function.type == RödaValue.Type.FUNCTION) {
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
				if (!list.isList()) error("a " + list.type + " doesn't have a length!");
				return valueFromInt(list.list.size());
			}

			if (!list.isList()) error("a " + list.type + " doesn't have elements!");
			
			if (exp.type == Expression.Type.ELEMENT) {
				int index = (int) evalExpression(exp.index, scope, in, out).impliciteResolve().num();
				if (index < 0) index = list.list.size()+index;
				if (list.list.size() <= index) error("array index out of bounds: index " + index + ", size " + list.list.size());
				return list.list.get(index);
			}

			if (exp.type == Expression.Type.SLICE) {
				int start, end;

				if (exp.index1 != null)
					start = (int) evalExpression(exp.index1, scope, in, out)
						.impliciteResolve().num();
				else start = 0;

				if (exp.index2 != null)
					end = (int) evalExpression(exp.index2, scope, in, out)
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
			if (!list.isList()) error("can't join a " + list.type);
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
			if (v.type != RödaValue.Type.FUNCTION && v.type != RödaValue.Type.NATIVE_FUNCTION) {
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
