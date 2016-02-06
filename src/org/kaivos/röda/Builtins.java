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
import java.io.BufferedReader;
import java.io.FileInputStream;

import java.nio.charset.Charset;
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
import static org.kaivos.röda.RödaStream.*;
import static org.kaivos.röda.Interpreter.*;
import static org.kaivos.röda.Parser.*;

class Builtins {

	private Builtins() {}

	static void populate(Interpreter I) {
		RödaScope S = I.G;

		/* Perusvirtaoperaatiot */

		S.setLocal("print", RödaNativeFunction.of("print", (typeargs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("print", 1, 0);
						return;
					}
					for (RödaValue value : args) {
						out.push(value);
					}
					out.push(RödaString.of("\n"));
				}, Arrays.asList(new Parameter("values", false)), true));
		S.setLocal("push", RödaNativeFunction.of("push", (typeargs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("push", 1, 0);
						return;
					}
					for (RödaValue value : args) {
						out.push(value);
					}
				}, Arrays.asList(new Parameter("values", false)), true));

		S.setLocal("pull", RödaNativeFunction.of("pull", (typeargs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("pull", 1, 0);
						return;
					}
					boolean readMode = false;
					for (RödaValue value : args) {
						if (value.isFlag("-r")) {
							readMode = true;
							continue;
						}
						checkReference("pull", value);
					        
					        RödaValue pulled = in.pull();
						if (pulled == null) {
							if (readMode) out.push(RödaBoolean.of(false));
							continue;
						}
						value.assign(pulled);
					        if (readMode) out.push(RödaBoolean.of(true));
					}
				}, Arrays.asList(new Parameter("variables", true)), true));

		/* Muuttujaoperaatiot */

		S.setLocal("undefine", RödaNativeFunction.of("undefine", (typeargs, args, scope, in, out) -> {
					for (RödaValue value : args) {
						checkReference("undefine", value);

						value.assign(null);
					}
				}, Arrays.asList(new Parameter("variables", true)), true));

		S.setLocal("name", RödaNativeFunction.of("name", (typeargs, args, scope, in, out) -> {
					for (RödaValue value : args) {
						if (!value.isReference())
							error("invalid argument for undefine: "
							      + "only references accepted");

						out.push(RödaString.of(value.target()));
					}
				}, Arrays.asList(new Parameter("variables", true)), true));

		S.setLocal("import", RödaNativeFunction.of("import", (typeargs, args, scope, in, out) -> {
				        for (RödaValue value : args) {
						checkString("import", value);
						String filename = value.str();
						File file = IOUtils.getMaybeRelativeFile(I.currentDir,
											 filename);
						I.loadFile(file, I.G);
					}
				}, Arrays.asList(new Parameter("files", false)), true));

                S.setLocal("assign_global", RödaNativeFunction.of("assign_global", (typeargs, args, scope, in, out) -> {
                                        String variableName = args.get(0).str();
					S.setLocal(variableName, args.get(1));
                                }, Arrays.asList(new Parameter("variable", false),
						 new Parameter("value", false)), false));

		/* Muut oleelliset kielen rakenteet */

		S.setLocal("identity", RödaNativeFunction.of("identity", (typeargs, args, scope, in, out) -> {
				        while (true) {
						RödaValue input = in.pull();
						if (input == null) break;
						out.push(input);
					}
				}, Collections.emptyList(), false));

		S.setLocal("error", RödaNativeFunction.of("error", (typeargs, args, scope, in, out) -> {
					checkArgs("error", 1, args.size());
					if (args.get(0).isString()) {
						error(args.get(0).str());
					}
					else if (!args.get(0).is("error")) {
						error("error: can't cast a " + args.get(0).typeString() + " to an error");
					}
					else error(args.get(0));
				}, Arrays.asList(new Parameter("errorObject", false)), false));

		S.setLocal("errprint", RödaNativeFunction.of("errprint", (typeargs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						while (true) {
							RödaValue input = in.pull();
							if (input == null) break;
							System.err.print(input.str());
						}
					}
					else for (RödaValue value : args) {
						System.err.print(value.str());
						out.push(RödaString.of("\n"));
					}
				}, Arrays.asList(new Parameter("values", false)), true));

		/* Täydentävät virtaoperaatiot */

		S.setLocal("head", RödaNativeFunction.of("head", (typeargs, args, scope, in, out) -> {
				        if (args.size() > 1) argumentOverflow("head", 1, args.size());
					if (args.size() == 0) {
						out.push(in.pull());
					}
					else {
						checkNumber("head", args.get(0));
						long num = args.get(0).num();
						for (int i = 0; i < num; i++) {
							RödaValue input = in.pull();
							if (input == null)
								error("head: input stream is closed");
							out.push(in.pull());
						}
					}
				}, Arrays.asList(new Parameter("number", false)), true));

		S.setLocal("tail", RödaNativeFunction.of("tail", (typeargs, args, scope, in, out) -> {
				        if (args.size() > 1) argumentOverflow("tail", 1, args.size());

					long numl;

					if (args.size() == 0) numl = 1;
					else {
						checkNumber("tail", args.get(0));
						numl = args.get(0).num();
						if (numl > Integer.MAX_VALUE)
							error("tail: too large number: " + numl);
					}

					int num = (int) numl;
					
					List<RödaValue> values = new ArrayList<>();
					for (RödaValue value : in) {
						values.add(value);
					}
					if (values.size() < num)
						error("tail: input stream is closed");

					for (int i = values.size()-num; i < values.size(); i++) {
						out.push(values.get(i));
					}
					
				}, Arrays.asList(new Parameter("number", false)), true));

		/* Yksinkertaiset merkkijonopohjaiset virtaoperaatiot */

		S.setLocal("search", RödaNativeFunction.of("search", (typeargs, args, scope, in, out) -> {
					if (args.size() < 1) argumentUnderflow("search", 1, 0);
					while (true) {
						RödaValue input = in.pull();
						if (input == null) break;
						
						String text = input.str();
						for (RödaValue value : args) {
							checkString("search", value);
							Pattern pattern = Pattern.compile(value.str());
							Matcher m = pattern.matcher(text);
							while (m.find()) {
								out.push(RödaString.of(m.group()));
							}
						}
					}
				}, Arrays.asList(new Parameter("patterns", false)), true));

		S.setLocal("match", RödaNativeFunction.of("match", (typeargs, args, scope, in, out) -> {
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
									String group = matcher.group(i);
									results[i] = RödaString.of(group != null
												   ? group
												   : "");
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
									String group = matcher.group(i);
									results[i] = RödaString.of(group != null
												   ? group
												   : "");
								}
								out.push(RödaList.of(results));
							}
							else out.push(RödaList.of());
						}
					}
				}, Arrays.asList(new Parameter("pattern", false), new Parameter("strings", false)), true));

		S.setLocal("replace", RödaNativeFunction.of("replace", (typeargs, args, scope, in, out) -> {
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
							out.push(RödaString.of(text));
						}
					} catch (PatternSyntaxException e) {
						error("replace: pattern syntax exception: " + e.getMessage());
					}
				}, Arrays.asList(new Parameter("patterns_and_replacements", false)), true));

		/* Parserit */

		S.setLocal("split", RödaNativeFunction.of("split", (typeargs, args, scope, in, out) -> {
					String separator = " ";
					boolean streamInput = true;
					boolean collect = false;
					for (int i = 0; i < args.size(); i++) {
						RödaValue value = args.get(i);
						if (value.isFlag("-s")) {
							RödaValue newSep = args.get(++i);
							checkString("split", newSep);
							separator = newSep.str();
							continue;
						}
						else if (value.isFlag("-c")) {
							collect = true;
							continue;
						}
						streamInput = false;
						checkString("split", value);
						String str = value.str();
						if (!collect) {
							for (String s : str.split(separator)) {
								out.push(RödaString.of(s));
							}
						}
						else {
							out.push(RödaList.of(Arrays.asList(str.split(separator))
									     .stream().map(RödaString::of).collect(toList())));
						}
					}
					if (streamInput) {
						while (true) {
							RödaValue value = in.pull();
							if (value == null) break;
							
							checkString("split", value);
							String str = value.str();
							if (!collect) {
								for (String s : str.split(separator)) {
									out.push(RödaString.of(s));
								}
							}
							else {
								out.push(RödaList.of(Arrays.asList(str.split(separator))
									     .stream().map(RödaString::of).collect(toList())));
							}
						}
					}
				}, Arrays.asList(new Parameter("flags_and_strings", false)), true));

		S.setLocal("json", RödaNativeFunction.of("json", (typeargs, args, scope, in, out) -> {
					boolean _stringOutput = false;
					boolean _iterativeOutput = false;
					while (args.size() > 0
					       && args.get(0).isFlag()) {
						RödaValue flag = args.get(0);
						if (flag.isFlag("-s")) _stringOutput = true;
						if (flag.isFlag("-i")) _iterativeOutput = true;
						else error("json: unknown option " + flag.str());
						args.remove(0);
					}
					boolean stringOutput = _stringOutput;
					boolean iterativeOutput = _iterativeOutput;
					Consumer<String> handler = code -> {
						JSONElement root = JSON.parseJSON(code);
						if (iterativeOutput) {
							for (JSONElement element : root) {
								if (!stringOutput) {
									RödaValue path = RödaList.of(element.getPath().stream()
												       .map(jk -> RödaString.of(jk.toString()) )
												       .collect(toList()));
									RödaValue value = RödaString.of(element.toString());
									out.push(RödaList.of(path, value));
								} else {
									out.push(RödaString.of(element.getPath().toString() + " " + element.toString()));
								}
							}
						} else { // rekursiivinen ulostulo
							// apuluokka rekursion mahdollistamiseksi
							class R<I> { I i; }
							R<java.util.function.Function<JSONElement, RödaValue>>
							makeRöda = new R<>();
							makeRöda.i = json -> {
								RödaValue elementName = RödaString.of(json.getElementName());
								RödaValue value;
								if (json instanceof JSONInteger) {
									value = RödaNumber.of(((JSONInteger) json).getValue());
								}
								else if (json instanceof JSONString) {
									value = RödaString.of(((JSONString) json).getValue());
								}
								else if (json instanceof JSONList) {
									value = RödaList.of(((JSONList) json).getElements()
											      .stream()
											      .map(j -> makeRöda.i.apply(j))
											      .collect(toList()));
								}
								else if (json instanceof JSONMap) {
									value = RödaList.of(((JSONMap) json).getElements().entrySet()
											      .stream()
											      .map(e -> RödaList.of(RödaString.of(e.getKey().getKey()),
														      makeRöda.i.apply(e.getValue())))
											      .collect(toList()));
								}
								else {
									value = RödaString.of(json.toString());
								}
								return RödaList.of(elementName, value);
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
				}, Arrays.asList(new Parameter("flags_and_code", false)), true));
		
		S.setLocal("parse_num", RödaNativeFunction.of("parse_num", (typeargs, args, scope, in, out) -> {
					int radix = 10;
					boolean tochr = false;
					while (args.size() > 0 && args.get(0).isFlag()) {
						String flag = args.remove(0).str();
						switch (flag) {
						case "-r":
							if (args.size() == 0)
								argumentUnderflow("parse_num", 1, args.size());
							checkNumber("parse_num", args.get(0));
							long radixl = args.remove(0).num();
							if (radixl > Integer.MAX_VALUE)
								error("parse_num: radix too great: " + radixl);
							radix = (int) radixl;
							break;
						case "-c":
							tochr = true;
						}
					}
					if (args.size() > 0) {
						for (RödaValue v : args) {
							checkString("parse_num", v);
							long lng = Long.parseLong(v.str(), radix);
							if (tochr) out.push(RödaString
									    .of(String.valueOf((char) lng)));
							else out.push(RödaNumber.of(lng));
						}
					} else {
						while (true) {
							RödaValue v = in.pull();
							if (v == null) break;
							checkString("parse_num", v);
							long lng = Long.parseLong(v.str(), radix);
							if (tochr) out.push(RödaString
									    .of(String.valueOf((char) lng)));
							else out.push(RödaNumber.of(lng));
						}
					}
				}, Arrays.asList(new Parameter("strings", false)), true));
		
		S.setLocal("btos", RödaNativeFunction.of("btos", (typeargs, args, scope, in, out) -> {
					Charset chrset = StandardCharsets.UTF_8;
					Consumer<RödaValue> convert = v -> {
							checkList("btos", v);
							byte[] arr = new byte[(int) v.list().size()];
							int c = 0;
							for (RödaValue i : v.list()) {
								checkNumber("btos", i);
								long l = i.num();
								if (l > Byte.MAX_VALUE*2)
									error("btos: too large byte: " + l);
								arr[c++] = (byte) l;
							}
							out.push(RödaString.of(new String(arr, chrset)));
					};
				        if (args.size() > 0) {
						for (RödaValue v : args) {
							convert.accept(v);
						}
					} else {
						while (true) {
							RödaValue v = in.pull();
							if (v == null) break;
							convert.accept(v);
						}
					}
				}, Arrays.asList(new Parameter("lists", false)), true));
		
		S.setLocal("expr", RödaNativeFunction.of("expr", (typeargs, args, scope, in, out) -> {
				        String expression = args.stream().map(RödaValue::str).collect(joining(" "));
					out.push(RödaString.of(String.valueOf(Calculator.eval(expression))));
				}, Arrays.asList(new Parameter("expressions", false)), true));

		S.setLocal("test", RödaNativeFunction.of("test", (typeargs, args, scope, in, out) -> {
					checkFlag("test", args.get(1));
					String operator = args.get(1).str();
					boolean not = false;
					if (operator.startsWith("-not_")) {
						operator = "-"+operator.substring(5);
						not = true;
					}
					RödaValue a1 = args.get(0);
					RödaValue a2 = args.get(2);
					switch (operator) {
					case "-eq": {
						out.push(RödaBoolean.of(a1.halfEq(a2)^not));
					} break;
					case "-strong_eq": {
						out.push(RödaBoolean.of(a1.strongEq(a2)^not));
					} break;
					case "-weak_eq": {
						out.push(RödaBoolean.of(a1.weakEq(a2)^not));
					} break;
					case "-matches": {
						checkString("test -matches", a1);
						checkString("test -matches", a2);
						out.push(RödaBoolean.of(a1.str().matches(a2.str())^not));
					} break;
					case "-lt": {
						checkNumber("test -lt", a1);
						checkNumber("test -lt", a2);
						out.push(RödaBoolean.of((a1.num() < a2.num())^not));
					} break;
					case "-le": {
						checkNumber("test -le", a1);
						checkNumber("test -le", a2);
						out.push(RödaBoolean.of((a1.num() <= a2.num())^not));
					} break;
					case "-gt": {
						checkNumber("test -gt", a1);
						checkNumber("test -gt", a2);
						out.push(RödaBoolean.of((a1.num() > a2.num())^not));
					} break;
					case "-ge": {
						checkNumber("test -ge", a1);
						checkNumber("test -ge", a2);
						out.push(RödaBoolean.of((a1.num() >= a2.num())^not));
					} break;
					default:
						error("test: unknown operator '" + operator + "'");
					}
				}, Arrays.asList(
						 new Parameter("value1", false),
						 new Parameter("operator", false),
						 new Parameter("value2", false)
						 ), false));

		/* Konstruktorit */
		
		S.setLocal("list", RödaNativeFunction.of("list", (typeargs, args, scope, in, out) -> {
				        out.push(RödaList.of(args));
				}, Arrays.asList(new Parameter("values", false)), true));

		S.setLocal("seq", RödaNativeFunction.of("seq", (typeargs, args, scope, in, out) -> {
					checkNumber("seq", args.get(0));
					checkNumber("seq", args.get(1));
					long from = args.get(0).num();
					long to = args.get(1).num();
					for (long i = from; i <= to; i++) out.push(RödaNumber.of(i));
				}, Arrays.asList(new Parameter("from", false), new Parameter("to", false)), false));

		S.setLocal("true", RödaNativeFunction.of("list", (typeargs, args, scope, in, out) -> {
				        out.push(RödaBoolean.of(true));
				}, Arrays.asList(), false));

		S.setLocal("false", RödaNativeFunction.of("false", (typeargs, args, scope, in, out) -> {
				        out.push(RödaBoolean.of(false));
				}, Arrays.asList(), false));

		/* Apuoperaatiot */

		S.setLocal("time", RödaNativeFunction.of("time", (typeargs, args, scope, in, out) -> {
				        out.push(RödaNumber.of((int) System.currentTimeMillis()));
				}, Arrays.asList(), false));

		Random rnd = new Random();
		
		S.setLocal("random", RödaNativeFunction.of("random", (typeargs, args, scope, in, out) -> {
					final int INTEGER=0,
						FLOAT=1,
						BOOLEAN=2;
					java.util.function.Function<Integer, RödaValue> next = i -> {
						switch (i) {
						case INTEGER: return RödaNumber.of(rnd.nextInt());
						case FLOAT: return RödaString.of(rnd.nextDouble()+"");
						case BOOLEAN: return RödaBoolean.of(rnd.nextBoolean());
						}
						return null;
					};
					int mode = BOOLEAN;
					if (args.size() == 1 && args.get(0).isFlag()) {
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
		
		S.setLocal("exec", RödaNativeFunction.of("exec", (typeargs, args, scope, in, out) -> {
					HashMap<String, String> envVars = new HashMap<>();
					class C{boolean c=false;}
					C lineMode = new C();
					while (args.size() > 0 && args.get(0).isFlag()) {
					        RödaValue flag = args.remove(0);
						if (flag.isFlag("-E")) {
							if (args.size() < 3)
								argumentUnderflow("exec", 4, args.size()+1);
							checkString("exec", args.get(0));
							checkString("exec", args.get(0));
							envVars.put(args.get(0).str(), args.get(1).str());
							args.remove(0);
							args.remove(0);
						} else if (flag.isFlag("-l")) {
							lineMode.c = true;
						} else if (flag.isFlag("-c")) {
							lineMode.c = false;
						}
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
								if (lineMode.c) {
									BufferedReader br = new BufferedReader(reader);
									while (true) {
										String str = br.readLine();
										if (str == null) break;
										out.push(RödaString.of(str));
									}
									br.close();
								}
								else {
									while (true) {
										int chr = reader.read();
										if (chr == -1) break;
										out.push(RödaString.of(String.valueOf((char) chr)));
									}
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
				}, Arrays.asList(new Parameter("flags_command_and_args", false)), true));

		/* Tiedosto-operaatiot */

		S.setLocal("cd", RödaNativeFunction.of("cd", (typeargs, args, scope, in, out) -> {
					checkArgs("cd", 1, args.size());
					checkString("cd", args.get(0));
					String dirname = args.get(0).str();
					File dir = IOUtils.getMaybeRelativeFile(I.currentDir, dirname);
					if (!dir.isDirectory()) {
						error("cd: not a directory");
					}
				        I.currentDir = dir;
				}, Arrays.asList(new Parameter("path", false)), false));

		S.setLocal("pwd", RödaNativeFunction.of("pwd", (typeargs, args, scope, in, out) -> {
					try {
						out.push(RödaString.of(I.currentDir.getCanonicalPath()));
					} catch (IOException e) {
						error(e);
					}
				}, Arrays.asList(), false));

		S.setLocal("write", RödaNativeFunction.of("write", (typeargs, args, scope, in, out) -> {
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
				}, Arrays.asList(new Parameter("file", false)), false));

		S.setLocal("cat", RödaNativeFunction.of("cat", (typeargs, args, scope, in, out) -> {
					if (args.size() < 1) argumentUnderflow("cat", 1, args.size());
					for (RödaValue value : args) {
						checkString("cat", value);
						String filename = value.str();
						File file = IOUtils.getMaybeRelativeFile(I.currentDir,
											 filename);
						for (String line : IOUtils.fileIterator(file)) {
							out.push(RödaString.of(line));
						}
					}
				}, Arrays.asList(new Parameter("files", false)), true));

		S.setLocal("file", RödaNativeFunction.of("file", (typeargs, args, scope, in, out) -> {
					if (args.size() < 1) argumentUnderflow("file", 1, args.size());
					for (int i = 0; i < args.size(); i++) {
						RödaValue flag = args.get(i);
						checkFlag("file", flag);
						RödaValue value = args.get(++i);
						checkString("file", value);
						String filename = value.str();
						File file = IOUtils.getMaybeRelativeFile(I.currentDir,
											 filename);
						if (flag.isFlag("-l"))
							out.push(RödaNumber.of(file.length()));
						else if (flag.isFlag("-e"))
							out.push(RödaBoolean.of(file.exists()));
						else if (flag.isFlag("-f"))
							out.push(RödaBoolean.of(file.isFile()));
						else if (flag.isFlag("-d"))
							out.push(RödaBoolean.of(file.isDirectory()));
						else if (flag.isFlag("-m")) try {
								out.push(RödaString
									 .of(Files
									     .probeContentType(file.toPath())));
							} catch (IOException e) { error(e); }
						else error("unknown command " + flag.str());
					}
				}, Arrays.asList(new Parameter("commands_and_files", false)), true));

		/* Verkko-operaatiot */

		S.setLocal("wcat", RödaNativeFunction.of("wcat", (typeargs, args, scope, in, out) -> {
					if (args.size() < 1) argumentUnderflow("wcat", 1, args.size());
					try {
						String useragent = "";
						String outputFile = "";
						for (int i = 0; i < args.size(); i++) {
							RödaValue _arg = args.get(i);
							
							if (_arg.isFlag("-U")) {
								RödaValue _ua = args.get(++i);
								checkString("wcat", _ua);
								useragent = _ua.str();
								continue;
							}
							if (_arg.isFlag("-O")) {
								RödaValue _of = args.get(++i);
								checkString("wcat", _of);
								outputFile = _of.str();
								continue;
							}
							
							checkString("wcat", _arg);
							String arg = _arg.str();
							
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
									out.push(RödaString.of(line));
								}
							}
							input.close();
						}
					} catch (MalformedURLException e) {
						error(e);
					} catch (IOException e) {
						error(e);
					}
				}, Arrays.asList(new Parameter("urls", false)), true));

		Record serverRecord = new Record("Server",
						 Collections.emptyList(),
						 null,
						 Arrays.asList(new Record.Field("accept", new Datatype("function")),
							       new Record.Field("close", new Datatype("function"))),
						 false);
		I.records.put("server", serverRecord);

		Record socketRecord = new Record("Socket",
						 Collections.emptyList(),
						 null,
						 Arrays.asList(new Record.Field("write", new Datatype("function")),
							       new Record.Field("read", new Datatype("function")),
							       new Record.Field("close", new Datatype("function")),
							       new Record.Field("ip", new Datatype("string")),
							       new Record.Field("hostname", new Datatype("string")),
							       new Record.Field("port", new Datatype("number")),
							       new Record.Field("localport", new Datatype("number"))),
						 false);
		I.records.put("socket", socketRecord);

		S.setLocal("server", RödaNativeFunction.of("server", (typeargs, args, scope, in, out) -> {
					checkArgs("server", 1, args.size());
					checkNumber("server", args.get(0));
					long port = args.get(0).num();
					if (port > Integer.MAX_VALUE)
						error("can't open port greater than " + Integer.MAX_VALUE);

					try {

						ServerSocket server = new ServerSocket((int) port);
						
						RödaValue serverObject = RödaRecordInstance
							.of(serverRecord,
							    Collections.emptyList(),
							    I.records);
						serverObject
							.setField("accept",
								  RödaNativeFunction
								  .of("Server.accept",
								      (ra, a, s, i, o) -> {
									      checkArgs("Server.accept",
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
												genericRead("Socket.read", _in, I));
									      socketObject
										      .setField("write",
												genericWrite("Socket.write", _out, I));
									      socketObject
										      .setField("close",
												RödaNativeFunction
												.of("Socket.close",
												    (r,A,z,j,u) -> {
													    checkArgs("Socket.close", 0, A.size());
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
												    false
												    )
												);
									      socketObject
										      .setField("ip",
												RödaString
												.of(socket
												    .getInetAddress()
												    .getHostAddress()
												    ));
									      socketObject
										      .setField("hostname",
												RödaString
												.of(socket
												    .getInetAddress()
												    .getCanonicalHostName()
												    ));
									      socketObject
										      .setField("port",
												RödaNumber
												.of(socket
												    .getPort()
												    ));
									      socketObject
										      .setField("localport",
												RödaNumber
												.of(socket
												    .getLocalPort()
												    ));
									      o.push(socketObject);
									  }, Collections.emptyList(), false));
						serverObject
							.setField("close",
							      RödaNativeFunction
							      .of("Server.close",
								  (ra, a, s, i, o) -> {
									  checkArgs("Server.close", 0, a.size());
									  
								  }, Collections.emptyList(), false));
						out.push(serverObject);
					} catch (IOException e) {
						error(e);
					}
				}, Arrays.asList(new Parameter("port", false)), false));

		// Säikeet

		Record threadRecord = new Record("Thread",
						 Collections.emptyList(),
						 null,
						 Arrays.asList(new Record.Field("start", new Datatype("function")),
							       new Record.Field("pull", new Datatype("function")),
							       new Record.Field("push", new Datatype("function"))),
						 false);
		I.records.put("thread", threadRecord);

		S.setLocal("thread", RödaNativeFunction.of("thread", (typeargs, args, scope, in, out) -> {
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
							I.exec("<Thread.start>", 0, function, Collections.emptyList(),
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
							      .of("Thread.start",
								  (ra, a, s, i, o) -> {
									  checkArgs("Thread.start", 0, a.size());
									  if (p.started)
										  error("Thread has already "
											+ "been executed");
									  p.started = true;
									  I.executor.execute(task);
								  }, Collections.emptyList(), false));
					threadObject.setField("pull",genericPull("Thread.pull", _out));
					threadObject.setField("push",genericPush("Thread.push", _in));
					out.push(threadObject);
				}, Arrays.asList(new Parameter("runnable", false)), false));
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
			    }, Arrays.asList(new Parameter("values", false)), true);
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
						    if (v.isFlag("-r")) {
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
			    }, Arrays.asList(new Parameter("variables", true)), true);
	}

	private static RödaValue genericWrite(String name, OutputStream _out, Interpreter I) {
		return RödaNativeFunction
			.of(name,
			    (ra, args, scope, in, out) -> {
				    try {
					    if (args.size() == 0) {
						    while (true) {
							    RödaValue v = in.pull();
							    if (v == null) break;
							    checkString(name, v);
							    _out.write(v.str().getBytes(StandardCharsets.UTF_8));
							    _out.flush();
						    }
					    }
					    else {
						    for (int i = 0; i < args.size(); i++) {
							    RödaValue v = args.get(i);
							    if (v.isFlag("-f")) {
								    RödaValue _file = args.get(++i);
								    checkString(name, _file);
								    File file = IOUtils
									    .getMaybeRelativeFile(I.currentDir,
												  _file.str());
								    try {
									    byte[] buf = new byte[2048];
									    InputStream is =
										    new FileInputStream(file);
									    int c = 0;
									    while ((c=is.read(buf, 0, buf.length))
										   > 0) {
										    _out.write(buf, 0, c);
										    _out.flush();
									    }
									    is.close();
								    } catch (IOException e) {
									    error(e);
								    }
								    
								    continue;
							    }
							    checkString(name, v);
							    _out.write(v.str().getBytes(StandardCharsets.UTF_8));
							    _out.flush();
						    }
					    }
				    } catch (IOException e) {
					    error(e);
				    }
			    }, Arrays.asList(new Parameter("values", false)), true);
	}

	private static RödaValue genericRead(String name, InputStream _in, Interpreter I) {
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
							    checkFlag(name, v);
							    String option = v.str();
							    RödaValue value;
							    switch (option) {
							    case "-b": {
								    RödaValue sizeVal = it.next().impliciteResolve();
								    checkNumber(name, sizeVal);
								    long size = sizeVal.num();
								    if (size > Integer.MAX_VALUE)
									    error(name + ": can't read more than "
										  + Integer.MAX_VALUE + " bytes "
										  + "at time");
								    byte[] data = new byte[(int) size];
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
			    }, Arrays.asList(new Parameter("variables", true)), true);
	}
}
