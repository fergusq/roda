package org.kaivos.röda;

import java.util.Random;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Iterator;

import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.joining;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;

import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import java.net.ServerSocket;
import java.net.Socket;

import org.kaivos.röda.Calculator;
import org.kaivos.röda.IOUtils;
import org.kaivos.röda.JSON;
import org.kaivos.röda.JSON.JSONElement;
import org.kaivos.röda.JSON.JSONInteger;
import org.kaivos.röda.JSON.JSONString;
import org.kaivos.röda.JSON.JSONList;
import org.kaivos.röda.JSON.JSONMap;

import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.*;
import static org.kaivos.röda.RödaValue.*;
import static org.kaivos.röda.RödaStream.*;
import static org.kaivos.röda.Interpreter.*;
import static org.kaivos.röda.Parser.*;

class Builtins {

	private Builtins() {}

	static void populate(Interpreter I) {
		RödaScope S = I.G;

		/* Perusvirtaoperaatiot */

		S.setLocal("print", valueFromNativeFunction("print", (rawArgs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("print", 1, 0);
						return;
					}
					for (RödaValue value : args) {
						out.push(value);
					}
					out.push(valueFromString("\n"));
				}, Arrays.asList(new Parameter("values", false)), true,
				new VoidStream(), new ValueStream()));
		S.setLocal("push", valueFromNativeFunction("push", (rawArgs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("push", 1, 0);
						return;
					}
					for (RödaValue value : args) {
						out.push(value);
					}
				}, Arrays.asList(new Parameter("values", false)), true,
				new VoidStream(), new ValueStream()));

		S.setLocal("pull", valueFromNativeFunction("pull", (rawArgs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("pull", 1, 0);
						return;
					}
					boolean readMode = false;
					for (RödaValue value : args) {
						if (value.isString() && value.str().equals("-r")) {
							readMode = true;
							continue;
						}
						checkReference("pull", value);
					        
					        RödaValue pulled = in.pull();
						if (pulled == null) {
							if (readMode) out.push(valueFromBoolean(false));
							continue;
						}
						value.assign(pulled);
					        if (readMode) out.push(valueFromBoolean(true));
					}
				}, Arrays.asList(new Parameter("variables", true)), true,
				new ValueStream(), new BooleanStream()));

		/* Muuttujaoperaatiot */

		S.setLocal("undefine", valueFromNativeFunction("undefine", (rawArgs, args, scope, in, out) -> {
					for (RödaValue value : args) {
						checkReference("undefine", value);

						value.assign(null);
					}
				}, Arrays.asList(new Parameter("variables", true)), true,
				new VoidStream(), new VoidStream()));

		S.setLocal("name", valueFromNativeFunction("name", (rawArgs, args, scope, in, out) -> {
					for (RödaValue value : args) {
						if (!value.isReference())
							error("invalid argument for undefine: "
							      + "only references accepted");

						out.push(valueFromString(value.target()));
					}
				}, Arrays.asList(new Parameter("variables", true)), true,
				new VoidStream(), new ValueStream()));

		S.setLocal("import", valueFromNativeFunction("import", (rawArgs, args, scope, in, out) -> {
				        for (RödaValue value : args) {
						checkString("import", value);
						String filename = value.str();
						File file = IOUtils.getMaybeRelativeFile(I.currentDir,
											 filename);
						I.loadFile(file, I.G);
					}
				}, Arrays.asList(new Parameter("files", false)), true,
				new VoidStream(), new VoidStream()));

		/* Täydentävät virtaoperaatiot */

		S.setLocal("head", valueFromNativeFunction("head", (rawArgs, args, scope, in, out) -> {
				        if (args.size() > 1) argumentOverflow("head", 1, args.size());
					if (args.size() == 0) {
						out.push(in.pull());
					}
					else {
						checkNumber("head", args.get(0));
						int num = args.get(0).num();
						for (int i = 0; i < num; i++) {
							RödaValue input = in.pull();
							if (input == null)
								error("head: input stream is closed");
							out.push(in.pull());
						}
					}
				}, Arrays.asList(new Parameter("number", false)), true,
				new ValueStream(), new ValueStream()));

		S.setLocal("tail", valueFromNativeFunction("tail", (rawArgs, args, scope, in, out) -> {
				        if (args.size() > 1) argumentOverflow("tail", 1, args.size());

					int num;

					if (args.size() == 0) num = 1;
					else {
						checkNumber("tail", args.get(0));
						num = args.get(0).num();
					}
					
					List<RödaValue> values = new ArrayList<>();
					for (RödaValue value : in) {
						values.add(value);
					}
					if (values.size() < num)
						error("tail: input stream is closed");

					for (int i = values.size()-num; i < values.size(); i++) {
						out.push(values.get(i));
					}
					
				}, Arrays.asList(new Parameter("number", false)), true,
				new ValueStream(), new ValueStream()));

		/* Yksinkertaiset merkkijonopohjaiset virtaoperaatiot */

		S.setLocal("grep", valueFromNativeFunction("grep", (rawArgs, args, scope, in, out) -> {
					if (args.size() < 1) argumentUnderflow("grep", 1, 0);
					checkString("grep", args.get(0));
					boolean onlyMatching = args.get(0).str().equals("-o");
					
					// basic mode
					if (!onlyMatching) {
						while (true) {
							RödaValue input = in.pull();
							if (input == null) break;
							
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
						while (true) {
							RödaValue input = in.pull();
							if (input == null) break;
						        
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
				}, Arrays.asList(new Parameter("patterns", false)), true,
				new ValueStream(), new ValueStream()));

		S.setLocal("match", valueFromNativeFunction("match", (rawArgs, args, scope, in, out) -> {
					if (args.size() < 1) argumentUnderflow("match", 1, 0);
					checkString("match", args.get(0));
				        String regex = args.get(0).str(); args.remove(0);
					Pattern pattern;
					try {
						pattern = Pattern.compile(regex);
					} catch (PatternSyntaxException e) {
						error("match: pattern syntax exception: " + e.getMessage());
						return;
					}

					if (args.size() > 0) {
						for (RödaValue arg : args) {
							checkString("match", arg);
							Matcher matcher = pattern.matcher(arg.str());
							if (matcher.matches()) {
								RödaValue[] results = new RödaValue[matcher.groupCount()+1];
								for (int i = 0; i < results.length; i++) {
									results[i] = RödaString.of(matcher.group(i));
								}
								out.push(RödaList.of(results));
							}
							else out.push(RödaList.of());
						}
					}
					else {
						while (true) {
							RödaValue input = in.pull();
							if (input == null) break;
							checkString("match", input);
							Matcher matcher = pattern.matcher(input.str());
							if (matcher.matches()) {
								RödaValue[] results = new RödaValue[matcher.groupCount()];
								for (int i = 0; i < results.length; i++) {
									results[i] = RödaString.of(matcher.group(i));
								}
								out.push(RödaList.of(results));
							}
							else out.push(RödaList.of());
						}
					}
				}, Arrays.asList(new Parameter("pattern", false), new Parameter("strings", false)), true,
				new ValueStream(), new ValueStream()));

		S.setLocal("replace", valueFromNativeFunction("replace", (rawArgs, args, scope, in, out) -> {
					if (args.size() % 2 != 0) error("invalid arguments for replace: even number required (got " + args.size() + ")");
					try {
						while (true) {
							RödaValue input = in.pull();
							if (input == null) break;
							
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
						error("replace: pattern syntax exception: " + e.getMessage());
					}
				}, Arrays.asList(new Parameter("patterns_and_replacements", false)), true,
				new ValueStream(), new ValueStream()));

		/* Parserit */

		S.setLocal("split", valueFromNativeFunction("split", (rawArgs, args, scope, in, out) -> {
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
				}, Arrays.asList(new Parameter("flags_and_strings", false)), true,
				new VoidStream(), new ValueStream()));

		S.setLocal("json", valueFromNativeFunction("json", (rawArgs, args, scope, in, out) -> {
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
						RödaValue value;
						while ((value = in.pull()) != null) {
							String code = value.str();
							handler.accept(code);
						}
					}
				}, Arrays.asList(new Parameter("flags_and_code", false)), true, new ValueStream(), new SingleValueStream()));
		
		S.setLocal("expr", valueFromNativeFunction("expr", (rawArgs, args, scope, in, out) -> {
				        String expression = args.stream().map(RödaValue::str).collect(joining(" "));
					out.push(valueFromString(String.valueOf(Calculator.eval(expression))));
				}, Arrays.asList(new Parameter("expressions", false)), true, new VoidStream(), new SingleValueStream()));

		S.setLocal("test", valueFromNativeFunction("test", (rawArgs, args, scope, in, out) -> {
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
						 ), false,
			new VoidStream(), new SingleValueStream()));

		/* Konstruktorit */
		
		S.setLocal("list", valueFromNativeFunction("list", (rawArgs, args, scope, in, out) -> {
				        out.push(valueFromList(args));
				}, Arrays.asList(new Parameter("values", false)), true,
				new VoidStream(), new SingleValueStream()));

		S.setLocal("seq", valueFromNativeFunction("seq", (rawArgs, args, scope, in, out) -> {
					checkNumber("seq", args.get(0));
					checkNumber("seq", args.get(1));
					int from = args.get(0).num();
					int to = args.get(1).num();
					for (int i = from; i <= to; i++) out.push(valueFromInt(i));
				}, Arrays.asList(new Parameter("from", false), new Parameter("to", false)), false,
				new VoidStream(), new ValueStream()));

		S.setLocal("true", valueFromNativeFunction("list", (rawArgs, args, scope, in, out) -> {
				        out.push(valueFromBoolean(true));
				}, Arrays.asList(), false, new VoidStream(), new SingleValueStream()));

		S.setLocal("false", valueFromNativeFunction("false", (rawArgs, args, scope, in, out) -> {
				        out.push(valueFromBoolean(false));
				}, Arrays.asList(), false, new VoidStream(), new SingleValueStream()));

		/* Apuoperaatiot */

		S.setLocal("time", valueFromNativeFunction("time", (rawArgs, args, scope, in, out) -> {
				        out.push(valueFromInt((int) System.currentTimeMillis()));
				}, Arrays.asList(), false, new VoidStream(), new SingleValueStream()));

		Random rnd = new Random();
		
		S.setLocal("random", valueFromNativeFunction("random", (rawArgs, args, scope, in, out) -> {
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
					if (args.size() == 1 && args.get(0).isString()) {
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
				}, Arrays.asList(new Parameter("flags_and_variables", true)), true,
				new VoidStream(), new SingleValueStream()));
		
		S.setLocal("exec", valueFromNativeFunction("exec", (rawArgs, args, scope, in, out) -> {
					HashMap<String, String> envVars = new HashMap<>();
					while (args.size() > 0 && args.get(0).isString()
					       && args.get(0).str().equals("-E")) {
						args.remove(0);
						if (args.size() < 3) argumentUnderflow("exec", 4, args.size()+1);
						checkString("exec", args.get(0));
						checkString("exec", args.get(0));
						envVars.put(args.get(0).str(), args.get(1).str());
						args.remove(0);
						args.remove(0);
					}
					if (args.size() < 1) argumentUnderflow("exec", 1, args.size());
				        List<String> params = args.stream().map(v -> v.str()).collect(toList());
					try {

						ProcessBuilder b = new ProcessBuilder(params);
						b.directory(I.currentDir);
						b.environment().putAll(envVars);
						Process p = b.start();
						InputStream pout = p.getInputStream();
						PrintWriter pin = new PrintWriter(p.getOutputStream());
						Runnable input = () -> {
							while (true) {
								RödaValue value = in.pull();
								if (value == null) break;
								pin.print(value.str());
								pin.flush();
							}
							pin.close();
						};
						Runnable output = () -> {
							InputStreamReader reader = new InputStreamReader(pout);
							try {
							        while (true) {
								        int chr = reader.read();
									if (chr == -1) break;
									out.push(valueFromString(String.valueOf((char) chr)));
								}
								reader.close();
							} catch (IOException e) {
								error(e);
							}
						};
						Future<?> futureIn = Interpreter.executor.submit(input);
						Future<?> futureOut = Interpreter.executor.submit(output);
						futureOut.get();
						in.pause();
						futureIn.get();
						in.unpause();
						p.waitFor();
					} catch (IOException e) {
					        error(e);
					} catch (InterruptedException e) {
					        error(e);
					} catch (ExecutionException e) {
						if (e.getCause() instanceof RödaException) {
							throw (RödaException) e.getCause();
						}
						error(e.getCause());
					}
				}, Arrays.asList(new Parameter("flags_command_and_args", false)), true,
				new ValueStream(), new ValueStream()));

		/* Tiedosto-operaatiot */

		S.setLocal("cd", valueFromNativeFunction("cd", (rawArgs, args, scope, in, out) -> {
					checkArgs("cd", 1, args.size());
					checkString("cd", args.get(0));
					String dirname = args.get(0).str();
					File dir = IOUtils.getMaybeRelativeFile(I.currentDir, dirname);
					if (!dir.isDirectory()) {
						error("cd: not a directory");
					}
				        I.currentDir = dir;
				}, Arrays.asList(new Parameter("path", false)), false,
				new VoidStream(), new VoidStream()));

		S.setLocal("pwd", valueFromNativeFunction("pwd", (rawArgs, args, scope, in, out) -> {
					try {
						out.push(valueFromString(I.currentDir.getCanonicalPath()));
					} catch (IOException e) {
						error(e);
					}
				}, Arrays.asList(), false,
				new VoidStream(), new ValueStream()));

		S.setLocal("write", valueFromNativeFunction("write", (rawArgs, args, scope, in, out) -> {
					checkString("write", args.get(0));
					String filename = args.get(0).str();
					File file = IOUtils.getMaybeRelativeFile(I.currentDir, filename);
					try {
						PrintWriter writer = new PrintWriter(file);
					        for (RödaValue input : in) {
							writer.print(input.str());
						}
						writer.close();
					} catch (IOException e) {
						error(e);
					}
				}, Arrays.asList(new Parameter("file", false)), false,
				new ValueStream(), new VoidStream()));

		S.setLocal("cat", valueFromNativeFunction("cat", (rawArgs, args, scope, in, out) -> {
					if (args.size() < 1) argumentUnderflow("wcat", 1, args.size());
					for (RödaValue value : args) {
						checkString("cat", value);
						String filename = value.str();
						File file = IOUtils.getMaybeRelativeFile(I.currentDir,
											 filename);
						for (String line : IOUtils.fileIterator(file)) {
							out.push(valueFromString(line));
						}
					}
				}, Arrays.asList(new Parameter("files", false)), true,
				new VoidStream(), new ValueStream()));

		/* Verkko-operaatiot */

		S.setLocal("wcat", valueFromNativeFunction("wcat", (rawArgs, args, scope, in, out) -> {
					if (args.size() < 1) argumentUnderflow("wcat", 1, args.size());
					try {
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
								for (String line : IOUtils.streamLineIterator(input)) {
									out.push(valueFromString(line));
								}
							}
							input.close();
						}
					} catch (MalformedURLException e) {
						error(e);
					} catch (IOException e) {
						error(e);
					}
				}, Arrays.asList(new Parameter("urls", false)), true,
				new VoidStream(), new ValueStream()));

		Record serverRecord = new Record("server",
						 Collections.emptyList(),
						 null,
						 Arrays.asList(new Record.Field("accept", new Datatype("function")),
							       new Record.Field("close", new Datatype("function"))),
						 false);
		I.records.put("server", serverRecord);

		Record socketRecord = new Record("socket",
						 Collections.emptyList(),
						 null,
						 Arrays.asList(new Record.Field("write", new Datatype("function")),
							       new Record.Field("read", new Datatype("function")),
							       new Record.Field("close", new Datatype("function"))),
						 false);
		I.records.put("socket", socketRecord);

		S.setLocal("server", valueFromNativeFunction("server", (rawArgs, args, scope, in, out) -> {
					checkArgs("server", 1, args.size());
					checkNumber("server", args.get(0));
					int port = args.get(0).num();

					try {

						ServerSocket server = new ServerSocket(port);
						
						RödaValue serverObject = RödaRecordInstance
							.of(serverRecord,
							    Collections.emptyList(),
							    I.records);
						serverObject
							.setField("accept",
								  RödaNativeFunction
								  .of("server.accept",
								      (ra, a, s, i, o) -> {
									      checkArgs("server.accept",
											0, a.size());
									      Socket socket;
									      InputStream _in;
									      OutputStream _out;
									      try {
										      socket = server.accept();
										      _in = socket.getInputStream();
										      _out = socket.getOutputStream();
									      } catch (IOException e) {
										      error(e);
										      return;
									      }
									      RödaValue socketObject =
										      RödaRecordInstance
										      .of(socketRecord,
											  Collections.emptyList(),
											  I.records);
									      socketObject
										      .setField("read",
												genericRead("socket.read", _in));
									      socketObject
										      .setField("write",
												genericWrite("socket.write", _out));
									      socketObject
										      .setField("close",
												RödaNativeFunction
												.of("socket.close",
												    (r,A,z,j,u) -> {
													    checkArgs("socket.close", 0, A.size());
													    try {
														    _out.close();
														    _in.close();
														    socket.close();
													    } catch (IOException e) {
														    error(e);
													    }
												    },
												    Collections
												    .emptyList(),
												    false,
												    new VoidStream(),
												    new VoidStream()
												    )
												);
									      o.push(socketObject);
									  }, Collections.emptyList(), false,
								  new VoidStream(), new ValueStream()));
						serverObject
							.setField("close",
							      RödaNativeFunction
							      .of("server.close",
								  (ra, a, s, i, o) -> {
									  checkArgs("server.close", 0, a.size());
									  
								  }, Collections.emptyList(), false,
								  new VoidStream(), new VoidStream()));
						out.push(serverObject);
					} catch (IOException e) {
						error(e);
					}
				}, Arrays.asList(new Parameter("port", false)), false,
				new VoidStream(), new ValueStream()));

		// Säikeet

		Record threadRecord = new Record("thread",
						 Collections.emptyList(),
						 null,
						 Arrays.asList(new Record.Field("start", new Datatype("function")),
							       new Record.Field("pull", new Datatype("function")),
							       new Record.Field("push", new Datatype("function"))),
						 false);
		I.records.put("thread", threadRecord);

		S.setLocal("thread", valueFromNativeFunction("thread", (rawArgs, args, scope, in, out) -> {
				        checkArgs("thread", 1, args.size());
				        RödaValue function = args.get(0);
					checkFunction("thread", function);

					RödaScope newScope =
						!function.isNativeFunction()
						&& function.localScope() != null
						? new RödaScope(function.localScope())
						: new RödaScope(I.G);
					RödaStream _in = RödaStream.makeStream();
					RödaStream _out = RödaStream.makeStream();
					
					class P { boolean started = false; }
					P p = new P();

					Runnable task = () -> {
						try {
							I.exec("<thread.start>", 0, function,
							       Collections.emptyList(), newScope, _in, _out);
						} catch (RödaException e) {
							System.err.println("[E] " + e.getMessage());
							for (String step : e.getStack()) {
								System.err.println(step);
							}
							if (e.getCause() != null) e.getCause().printStackTrace();
						}
						_out.finish();
					};

					RödaValue threadObject = RödaRecordInstance.of(threadRecord,
										       Collections.emptyList(),
										       I.records);
					threadObject.setField("start",
							      RödaNativeFunction
							      .of("thread.start",
								  (ra, a, s, i, o) -> {
									  checkArgs("thread.start", 0, a.size());
									  if (p.started)
										  error("thread has already "
											+ "been executed");
									  p.started = true;
									  I.executor.execute(task);
								  }, Collections.emptyList(), false,
								  new VoidStream(), new VoidStream()));
					threadObject.setField("pull",genericPull("thread.pull", _out));
					threadObject.setField("push",genericPush("thread.push", _in));
					out.push(threadObject);
				}, Arrays.asList(new Parameter("runnable", false)), false,
				new VoidStream(), new ValueStream()));
	}

	private static RödaValue genericPush(String name, RödaStream _out) {
		return RödaNativeFunction
			.of(name,
			    (ra, a, s, i, o) -> {
				    if (a.size() == 0) {
					    while (true) {
						    RödaValue v = i.pull();
						    if (v == null) break;
						    _out.push(v);
					    }
				    }
				    else {
					    for (RödaValue v : a) {
						    _out.push(v);
					    }
				    }
			    }, Arrays.asList(new Parameter("values", false)), true,
			    new ValueStream(), new VoidStream());
	}

	private static RödaValue genericPull(String name, RödaStream _in) {
		return RödaNativeFunction
			.of(name,
			    (ra, a, s, i, o) -> {
				    if (a.size() == 0) {
					    while (true) {
						    RödaValue v = _in.pull();
						    if (v == null) break;
						    o.push(v);
					    }
				    }
				    else {
					    boolean readMode = false;
					    for (RödaValue v : a) {
						    if (v.isString()
							&& v.str().equals("-r")) {
							    readMode = true;
							    continue;
						    }
						    checkReference(name, v);
						    RödaValue pulled
							    = _in.pull();
						    if (pulled == null) {
							    if (readMode) {
								    o.push(RödaBoolean.of(false));
							    }
							    continue;
						    }
						    v.assign(pulled);
						    if (readMode) {
							    o.push(RödaBoolean.of(true));
						    }
					    }
				    }
			    }, Arrays.asList(new Parameter("variables", true)), true,
			    new VoidStream(),
			    new ValueStream());
	}

	private static RödaValue genericWrite(String name, OutputStream _out) {
		return RödaNativeFunction
			.of(name,
			    (ra, args, scope, in, out) -> {
				    try {
					    if (args.size() == 0) {
						    while (true) {
							    RödaValue v = in.pull();
							    if (v == null) break;
							    _out.write(v.str().getBytes(StandardCharsets.UTF_8));
						    }
					    }
					    else {
						    for (RödaValue v : args) {
							    _out.write(v.str().getBytes(StandardCharsets.UTF_8));
						    }
					    }
				    } catch (IOException e) {
					    error(e);
				    }
			    }, Arrays.asList(new Parameter("values", false)), true,
			    new ValueStream(), new VoidStream());
	}

	private static RödaValue genericRead(String name, InputStream _in) {
		return RödaNativeFunction
			.of(name,
			    (ra, args, scope, in, out) -> {
				    try {
					    if (args.size() == 0) {
						    while (true) {
							    int i = _in.read();
							    if (i == -1) break;
							    out.push(RödaNumber.of(i));
						    }
					    }
					    else {
						    boolean byteMode = false;
						    Iterator<RödaValue> it = args.iterator();
						    while (it.hasNext()) {
							    RödaValue v = it.next();
							    checkString(name, v);
							    String option = v.str();
							    RödaValue value;
							    switch (option) {
							    case "-b": {
								    RödaValue sizeVal = it.next().impliciteResolve();
								    checkNumber(name, sizeVal);
								    int size = sizeVal.num();
								    byte[] data = new byte[size];
								    _in.read(data);
								    value = RödaString.of(new String(data, StandardCharsets.UTF_8));
							    } break;
							    case "-l": {
								    List<Byte> bytes = new ArrayList<>(256);
								    int i;
								    do {
									    i = _in.read();
									    if (i == -1) break;
									    bytes.add((byte) i);
								    } while (i != '\n');
								    byte[] byteArr = new byte[bytes.size()];
								    for (int j = 0; j < bytes.size(); j++) byteArr[j] = bytes.get(j);
								    value = RödaString.of(new String(byteArr, StandardCharsets.UTF_8));
							    } break;
							    default:
								    error(name + ": unknown option '" + option + "'");
								    value = null;
							    }
							    RödaValue refVal = it.next();
							    checkReference(name, refVal);
							    refVal.assign(value);
						    }
					    }
				    } catch (IOException e) {
					    error(e);
				    }
			    }, Arrays.asList(new Parameter("variables", true)), true,
			    new VoidStream(),
			    new ValueStream());
	}
}
