package org.kaivos.röda;

import java.util.Random;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.joining;

import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStream;
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

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.RödaValue.*;
import static org.kaivos.röda.Interpreter.*;
import static org.kaivos.röda.Parser.*;

class Builtins {
	private Builtins() {}

	static void populate(RödaScope S) {
		S.setLocal("push", valueFromNativeFunction("push", (rawArgs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("push", 1, 0);
						return;
					}
					for (RödaValue value : args) {
						out.push(value);
					}
				}, Arrays.asList(new Parameter("values", false)), true));

		S.setLocal("pull", valueFromNativeFunction("pull", (rawArgs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("pull", 1, 0);
						return;
					}
					boolean readMode = false;
					for (RödaValue value : rawArgs) {
						if (value.isString() && value.text.equals("-r")) {
							readMode = true;
							continue;
						}
						else if (!value.isReference())
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

		S.setLocal("seq", valueFromNativeFunction("seq", (rawArgs, args, scope, in, out) -> {
					checkNumber("seq", args.get(0));
					checkNumber("seq", args.get(1));
					int from = args.get(0).num();
					int to = args.get(1).num();
					for (int i = from; i <= to; i++) out.push(valueFromInt(i));
				}, Arrays.asList(new Parameter("from", false), new Parameter("to", false)), false));

		S.setLocal("grep", valueFromNativeFunction("grep", (rawArgs, args, scope, in, out) -> {
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

		S.setLocal("replace", valueFromNativeFunction("replace", (rawArgs, args, scope, in, out) -> {
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
				}, Arrays.asList(new Parameter("flags_and_strings", false)), true));

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
						for (RödaValue value : in) {
							String code = value.str();
							handler.accept(code);
						}
					}
				}, Arrays.asList(new Parameter("flags_and_code", false)), true));

		S.setLocal("list", valueFromNativeFunction("list", (rawArgs, args, scope, in, out) -> {
				        out.push(valueFromList(args));
				}, Arrays.asList(new Parameter("values", false)), true));

		S.setLocal("true", valueFromNativeFunction("list", (rawArgs, args, scope, in, out) -> {
				        out.push(valueFromBoolean(true));
				}, Arrays.asList(), false));

		S.setLocal("false", valueFromNativeFunction("false", (rawArgs, args, scope, in, out) -> {
				        out.push(valueFromBoolean(false));
				}, Arrays.asList(), false));

		S.setLocal("time", valueFromNativeFunction("time", (rawArgs, args, scope, in, out) -> {
				        out.push(valueFromInt((int) System.currentTimeMillis()));
				}, Arrays.asList(), false));

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
				}, Arrays.asList(new Parameter("flags_and_variables", true)), true));
		
		S.setLocal("expr", valueFromNativeFunction("expr", (rawArgs, args, scope, in, out) -> {
				        String expression = args.stream().map(RödaValue::str).collect(joining(" "));
					out.push(valueFromInt(Calculator.eval(expression)));
				}, Arrays.asList(new Parameter("expressions", false)), true));

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
						 ), false));

		S.setLocal("head", valueFromNativeFunction("head", (rawArgs, args, scope, in, out) -> {
				        if (args.size() > 1) argumentOverflow("head", 1, args.size());
					if (args.size() == 0) {
						out.push(in.pull());
					}
					else {
						checkNumber("head", args.get(0));
						int num = args.get(0).num();
						for (int i = 0; i < num; i++) out.push(in.pull());
					}
				}, Arrays.asList(new Parameter("number", false)), true));

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

					for (int i = values.size()-num; i < values.size(); i++) {
						out.push(values.get(i));
					}
					
				}, Arrays.asList(new Parameter("number", false)), true));

		S.setLocal("write", valueFromNativeFunction("write", (rawArgs, args, scope, in, out) -> {
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

		S.setLocal("cat", valueFromNativeFunction("cat", (rawArgs, args, scope, in, out) -> {
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

		S.setLocal("wcat", valueFromNativeFunction("wcat", (rawArgs, args, scope, in, out) -> {
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
		
		S.setLocal("exec", valueFromNativeFunction("exec", (rawArgs, args, scope, in, out) -> {
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
}
