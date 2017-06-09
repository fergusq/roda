package org.kaivos.röda;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.kaivos.röda.Interpreter.INTERPRETER;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jline.keymap.KeyMap;
import org.jline.reader.Candidate;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.InfoCmp.Capability;
import org.kaivos.nept.parser.ParsingException;
import org.kaivos.röda.Interpreter.RödaException;
import org.kaivos.röda.RödaStream.ISStream;
import org.kaivos.röda.RödaStream.OSStream;
import org.kaivos.röda.commands.StreamPopulator;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

/**
 * A simple stream language
 */
public class Röda {
	
	public static final String RÖDA_VERSION_STRING = "0.13-alpha";
	
	private static void printRödaException(Interpreter.RödaException e) {
		System.err.println("[" + e.getErrorObject().basicIdentity() + "] " + e.getMessage());
		for (String step : e.getStack()) {
			System.err.println(step);
		}
		if (e.getCauses() != null && e.getCauses().length > 0) {
			System.err.println("caused by:");
			for (Throwable cause : e.getCauses())
				if (cause instanceof Interpreter.RödaException) printRödaException((RödaException) cause);
				else cause.printStackTrace();
		}
	}
	
	private static ISStream STDIN;
	private static OSStream STDOUT, STDERR;
	static {
		InputStreamReader ir = new InputStreamReader(System.in);
		BufferedReader in = new BufferedReader(ir);
		PrintWriter out = new PrintWriter(System.out);
		PrintWriter err = new PrintWriter(System.err);
		STDIN = new ISStream(in);
		STDOUT = new OSStream(out);
		STDERR = new OSStream(err);
	}
	
	private static String prompt;
	private static int keywordColor = 2, variableColor = 6;
	
	private static void interpretEOption(List<String> eval) {
		try {
			for (String stmt : eval) INTERPRETER.interpretStatement(stmt, "<option -e>", STDIN, STDOUT);
		} catch (ParsingException e) {
			System.err.println("[E] " + e.getMessage());
		} catch (Interpreter.RödaException e) {
			printRödaException(e);
		}
	}
	
	public static void main(String[] args) throws IOException {
		String file = null;
		List<String> eval = new ArrayList<>();
		List<String> argsForRöda = new ArrayList<>();
		boolean interactive = System.console() != null, forcedI = false, disableInteraction = false,
				enableDebug = true, enableProfiling = false, divideByInvocations = false, singleThreadMode = false;
		
		for (int i = 0; i < args.length; i++) {
			if (file != null) {
				argsForRöda.add(args[i]);
				continue;
			}
			switch (args[i]) {
			case "-p":
				prompt = args[++i];
				continue;
			case "-P":
				prompt = "";
				continue;
			case "-e":
				eval.add(args[++i]);
				continue;
			case "-i":
				interactive = true;
				forcedI = true;
				continue;
			case "-I":
				interactive = false;
				continue;
			case "-n":
				disableInteraction = true;
				continue;
			case "-D":
				enableDebug = false;
				continue;
			case "-t":
				enableProfiling = true;
				continue;
			case "--per-invocation":
				divideByInvocations = true;
				break;
			case "-s":
				singleThreadMode = true;
				continue;
			case "-v":
			case "--version":
				System.out.println("Röda " + RÖDA_VERSION_STRING);
				return;
			case "-h":
			case "--help": {
				System.out.println("Usage: röda [options] file | röda [options] -i | röda [options]");
				System.out.println("Available options:");
				System.out.println("-D               Disable stack tracing (may speed up execution a little)");
				System.out.println("-e stmt          Evaluate the given statement before executing the given files");
				System.out.println("-i               Enable console mode");
				System.out.println("-I               Disable console mode");
				System.out.println("-n               Disable interactive mode");
				System.out.println("-p prompt        Change the prompt in interactive mode");
				System.out.println("-P               Disable prompt in interactive mode");
				System.out.println("--per-invocation Divide CPU time by invocation number in profiler output");
				System.out.println("-s               Enable single thread mode");
				System.out.println("-t               Enable time profiler");
				System.out.println("-v, --version    Show the version number of the interpreter");
				System.out.println("-h, --help       Show this help text");
				return;
			}
			default:
				file = args[i];
				continue;
			}
		}

		if (prompt == null) prompt = interactive ? "! " : "";

		if ((file != null && forcedI) || (file != null && disableInteraction)) {
			System.err.println("Usage: röda [options] file | röda [options] -n | röda [options] [-i|-I]");
			System.exit(1);
			return;
		}
		
		INTERPRETER.enableDebug = enableDebug;
		INTERPRETER.enableProfiling = enableProfiling;
		INTERPRETER.singleThreadMode = singleThreadMode;
		
		INTERPRETER.populateBuiltins();
		
		INTERPRETER.G.setLocal("STDIN", StreamPopulator.createStreamObj(STDIN));
		INTERPRETER.G.setLocal("STDOUT", StreamPopulator.createStreamObj(STDOUT));
		INTERPRETER.G.setLocal("STDERR", StreamPopulator.createStreamObj(STDERR));

		INTERPRETER.G.setLocal("inputMode", RödaNativeFunction.of("inputMode", (ta, a, k, s, i, o) -> {
			RödaValue arg = a.get(0);
			if (arg.is(RödaValue.INTEGER)) {
				if (arg.integer() == 0) {
					STDIN.setMode(RödaStream.ISStreamMode.LINE);
				}
				else if (arg.integer() == 1) {
					STDIN.setMode(RödaStream.ISStreamMode.CHARACTER);
				}
				else Interpreter.illegalArguments("illegal argument for 'inputMode': " + arg.str());
			}
			else if (arg.is(RödaValue.STRING)) {
				switch (arg.str()) {
				case "line":
				case "l":
					STDIN.setMode(RödaStream.ISStreamMode.LINE);
					break;
				case "character":
				case "c":
					STDIN.setMode(RödaStream.ISStreamMode.CHARACTER);
					break;
				default:
					Interpreter.illegalArguments("illegal argument for 'inputMode': " + arg.str());
					break;
				}
			}
		}, Arrays.asList(new Parameter("mode", false)), false));

		INTERPRETER.G.setLocal("ifStdIn", RödaNativeFunction.of("ifStdIn", (ta, a, k, s, i, o) -> {
			if (i == STDIN) {
				INTERPRETER.exec("<io setup>", -1, a.get(0), emptyList(), emptyList(), emptyMap(), s, i, o);
			}
		}, Arrays.asList(new Parameter("callback", false, RödaValue.FUNCTION)), false));

		INTERPRETER.G.setLocal("ifStdOut", RödaNativeFunction.of("ifStdOut", (ta, a, k, s, i, o) -> {
			if (o == STDOUT) {
				INTERPRETER.exec("<io setup>", -1, a.get(0), emptyList(), emptyList(), emptyMap(), s, i, o);
			}
		}, Arrays.asList(new Parameter("callback", false, RödaValue.FUNCTION)), false));

		if (file != null) {
			File fileObj = new File(file);
			interpretEOption(eval);
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileObj)));
			String code = "";
			String line = "";
			while ((line = in.readLine()) != null) {
				code += line + "\n";
			}
			in.close();
			List<RödaValue> valueArgs = argsForRöda.stream()
				.map(RödaString::of)
				.collect(Collectors.toList());
			try {
				INTERPRETER.G.setLocal("SOURCE_FILE", RödaString.of(fileObj.getAbsolutePath()));
				INTERPRETER.G.setLocal("SOURCE_DIR", RödaString.of(fileObj.getAbsoluteFile().getParentFile().getAbsolutePath()));
				INTERPRETER.interpret(code, valueArgs, file, STDIN, STDOUT);
			} catch (ParsingException e) {
				System.err.println("[E] " + e.getMessage());
			} catch (Interpreter.RödaException e) {
				printRödaException(e);
			}
		} else if (!disableInteraction && interactive && System.console() != null) {

			File historyFile = new File(System.getProperty("user.home") + "/.rödahist");
			
			DefaultParser parser = new DefaultParser() {
				
				@Override
				public ParsedLine parse(final String line, final int cursor, ParseContext context) {
					List<String> words = new LinkedList<>();
					StringBuilder current = new StringBuilder();
					int wordCursor = -1;
					int wordIndex = -1;

					for (int i = 0; (line != null) && (i < line.length()); i++) {
						if (i == cursor) {
							wordIndex = words.size();
							wordCursor = current.length();
						}
						if (isDelimiterChar(line, i)) {
							if (current.length() > 0) {
								words.add(current.toString());
								current.setLength(0);
							}
							if (!Character.isWhitespace(line.charAt(i))) {
								words.add(Character.toString(line.charAt(i)));
							}
						} else {
		                    current.append(line.charAt(i));
		                }
					}

					if (current.length() > 0 || cursor == line.length()) {
						words.add(current.toString());
					}

					if (cursor == line.length()) {
						wordIndex = words.size() - 1;
						wordCursor = words.get(words.size() - 1).length();
					}

					return new ArgumentList(line, words, wordIndex, wordCursor, cursor);
				}

				@Override
				public boolean isDelimiterChar(CharSequence buffer, int pos) {
					char c = buffer.charAt(pos);
					return c != '.' && Parser.operatorCharacters.indexOf(c) >= 0
							|| Character.isWhitespace(c);
				}
			};
			
			parser.setEscapeChars(new char[0]);
			
			Set<String> builtins = INTERPRETER.G.map.keySet();
			
			Terminal terminal = TerminalBuilder.terminal();
			LineReader in = LineReaderBuilder.builder()
					.terminal(terminal)
					.appName("Röda")
					.variable(LineReader.HISTORY_FILE, historyFile)
					.variable(LineReader.COMMENT_BEGIN, "#!")
					.variable(LineReader.SECONDARY_PROMPT_PATTERN, "%P > ")
					.parser(parser)
					.highlighter((r, b) -> {
						AttributedStringBuilder sb = new AttributedStringBuilder();
						StringBuilder current = new StringBuilder();
						Consumer<String> style = s -> {
							boolean isWord = s.matches("[\\w']+");
							if (!Parser.validIdentifier(s) && isWord
									//|| s.equals("{") || s.equals("}") || s.equals(";") || s.equals("|")
									) {
								sb.style(sb.style().foreground(keywordColor));
								sb.append(s);
								sb.style(sb.style().foregroundOff());
							}
							else if (isWord && builtins.contains(s)) {
								sb.style(sb.style().foreground(variableColor));
								sb.style(sb.style().bold());
								sb.append(s);
								sb.style(sb.style().boldOff());
								sb.style(sb.style().foregroundOff());
							}
							else if (s.matches("[0-9]+")) {
								sb.style(sb.style().faint());
								sb.append(s);
								sb.style(sb.style().faintOff());
							}
							else sb.append(s);
						};
						boolean simpleQuoteOn = false, backtickQuoteOn = false;
						int interpolationDepth = 0;
						for (int i = 0; i < b.length(); i++) {
							char c = b.charAt(i);
							if (c == '"' && !backtickQuoteOn) {
								simpleQuoteOn = !simpleQuoteOn;
								if (simpleQuoteOn) {
									style.accept(current.toString());
									current.setLength(0);
									sb.style(sb.style().faint());
								}
							}
							else if (c == '`' && !simpleQuoteOn && interpolationDepth == 0) {
								backtickQuoteOn = !backtickQuoteOn;
								if (backtickQuoteOn) {
									style.accept(current.toString());
									current.setLength(0);
									sb.style(sb.style().faint());
								}
							}
							if (simpleQuoteOn) {
								sb.append(c);
								if (c == '\\' && i != b.length()-1) sb.append(b.charAt(++i));
							}
							else if (backtickQuoteOn) {
								sb.append(c);
								if (c == '$' && i != b.length()-1) {
									if (b.charAt(i+1) == '{') {
										sb.append('{'); i++;
										backtickQuoteOn = false;
										interpolationDepth = 1;
										sb.style(sb.style().faintOff());
										continue;
									}
								}
							}
							else if (c == '}' && interpolationDepth == 1) {
								style.accept(current.toString());
								current.setLength(0);
								interpolationDepth = 0;
								backtickQuoteOn = true;
								sb.style(sb.style().faint());
								sb.append(c);
							}
							else if (Parser.operatorCharacters.indexOf(c) >= 0 || Character.isWhitespace(c)) {
								style.accept(current.toString());
								current.setLength(0);
								style.accept(Character.toString(c));
							}
							else if (c == ' ') { // NBSP
								sb.style(sb.style().inverse());
								sb.append(c);
								sb.style(sb.style().inverseOff());
							}
							else {
								current.append(c);
							}
							if (interpolationDepth > 0) {
								if (c == '{') interpolationDepth++;
								else if (c == '}') interpolationDepth--;
							}
							if ((c == '"' || c == '`') && !simpleQuoteOn && !backtickQuoteOn) {
								sb.style(sb.style().faintOff());
							}
						}
						style.accept(current.toString());
						return sb.toAttributedString();
					})
					.completer((r, l, c) -> {
						int quoteChars = 0, quoteIndex = -1;
						for (int i = 0; i < l.wordIndex(); i++) {
							if (l.words().get(i).equals("\"")) {
								quoteChars++;
								quoteIndex = i;
							}
							else if (l.words().get(i).equals("\\")) i++;
						}
						if (quoteChars % 2 == 0) {
							boolean isPiped = l.wordIndex() == 0 || l.words().get(l.wordIndex()-1).equals("|");
							TreeSet<String> vars = new TreeSet<>(builtins);
							for (String match : vars.tailSet(l.word())) {
								if (!match.startsWith(l.word())) break;
								
								RödaValue val = INTERPRETER.G.map.get(match);
								
								String cand;
								if (isPiped) cand = match;
								else if (val.is(RödaValue.FUNCTION)) {
									if (val.is(RödaValue.NFUNCTION)
											&& !val.nfunction().isVarargs
											&& val.nfunction().parameters.isEmpty()
											&& !val.nfunction().isKwVarargs
											&& val.nfunction().kwparameters.isEmpty())
										cand = match + "()";
									else
										cand = match + "(";
								}
								else if (val.is(RödaValue.LIST) || val.is(RödaValue.MAP)) cand = match + "[";
								else cand = match;
								
								c.add(new Candidate(cand, match, null, val.str(), null, null, isPiped));
							}
						}
						else {
							try {
								String currentPath = "";
								for (int i = quoteIndex + 1; i <= l.wordIndex(); i++) {
									currentPath += l.words().get(i);
								}
								File currentFile = IOUtils.getMaybeRelativeFile(INTERPRETER.currentDir, currentPath);
								TreeSet<String> files;
								String fileToComplete;
								if (!currentPath.endsWith("/")) {
									if (currentFile.isDirectory()) {
										c.add(new Candidate("/"));
									}
								}
								if (currentPath.indexOf('/') >= 0) {
									File currentDir = IOUtils.getMaybeRelativeFile(INTERPRETER.currentDir,
											currentPath.substring(0, currentPath.lastIndexOf('/')));
									
									files = new TreeSet<>(Files.list(currentDir.toPath())
											.map(p -> currentDir.toPath().relativize(p).toString()
													+ (p.toFile().isDirectory() ? "/" : ""))
											.collect(Collectors.toSet()));
									fileToComplete = currentPath.substring(currentPath.lastIndexOf('/')+1);
								}
								else {
									files = new TreeSet<>(Files.list(INTERPRETER.currentDir.toPath())
											.map(p -> INTERPRETER.currentDir.toPath().relativize(p).toString()
													+ (p.toFile().isDirectory() ? "/" : ""))
											.collect(Collectors.toSet()));
									fileToComplete = currentPath;
								}
								for (String match : files.tailSet(fileToComplete)) {
									if (!match.startsWith(fileToComplete)) break;
									c.add(new Candidate(match, match, null, null, null, null, false));
								}
							} catch (IOException e) {
								Interpreter.error(e);
							}
						}
					})
					.build();

			in.unsetOpt(LineReader.Option.INSERT_TAB);
			in.setOpt(LineReader.Option.DISABLE_EVENT_EXPANSION);
			
			in.getKeyMaps().get(LineReader.EMACS)
				.bind(new Reference(LineReader.VI_OPEN_LINE_BELOW), KeyMap.alt(KeyMap.key(terminal, Capability.newline)));
			
			INTERPRETER.G.setLocal("jlineVar", RödaNativeFunction.of("jlineVar", (ta, a, k, s, i, o) -> {
				if (a.size() > 2) Interpreter.argumentOverflow("jlineVar", 2, a.size());
				else if (a.size() == 2) in.setVariable(a.get(0).str(), a.get(1).str());
				else if (a.size() == 1) {
					Object var = in.getVariable(a.get(0).str());
					if (var != null)
						o.push(RödaString.of(var.toString()));
				}
			}, Arrays.asList(new Parameter("var", false, RödaValue.STRING), new Parameter("values", false)), true));
			
			INTERPRETER.G.setLocal("kwColor", RödaNativeFunction.of("kwColor", (ta, a, k, s, i, o) -> {
				keywordColor = (int) a.get(0).integer();
			}, Arrays.asList(new Parameter("color", false, RödaValue.INTEGER)), false));
			INTERPRETER.G.setLocal("varColor", RödaNativeFunction.of("kwColor", (ta, a, k, s, i, o) -> {
				variableColor = (int) a.get(0).integer();
			}, Arrays.asList(new Parameter("color", false, RödaValue.INTEGER)), false));

			INTERPRETER.G.setLocal("prompt", RödaNativeFunction.of("prompt", (ta, a, k, s, i, o) -> {
				Interpreter.checkString("prompt", a.get(0));
				prompt = a.get(0).str();
			}, Arrays.asList(new Parameter("prompt_string", false)), false));

			INTERPRETER.G.setLocal("getLine", RödaNativeFunction.of("getLine", (ta, a, k, s, i, o) -> {
				o.push(RödaString.of(in.readLine("? ")));
			}, Arrays.asList(), false));
			
			interpretEOption(eval);
			
			String line = "";
			int i = 1;
			while (true) {
				try {
					line = in.readLine(prompt);
				} catch (EndOfFileException e) {
					break;
				} catch (UserInterruptException e) {
					continue;
				}
				if (!line.trim().isEmpty()) {
					try {
						INTERPRETER.interpretStatement(line, "<line "+ i++ +">", STDIN, STDOUT);
					} catch (ParsingException e) {
						System.err.println("[E] " + e.getMessage());
					} catch (Interpreter.RödaException e) {
						printRödaException(e);
					}
				}
			}
			
			in.getHistory().save();

			System.out.println();

		} else if (!disableInteraction) {
			interpretEOption(eval);

			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			String line = "";
			int i = 1;
			System.out.print(prompt);
			while ((line = in.readLine()) != null) {
				if (!line.trim().isEmpty()) {
					try {
						INTERPRETER.interpretStatement(line, "<line "+ i++ +">", STDIN, STDOUT);
					} catch (ParsingException e) {
						System.err.println("[E] " + e.getMessage());
					} catch (Interpreter.RödaException e) {
						printRödaException(e);
					}
				}
				System.out.print(prompt);
			}
		}
		else if (disableInteraction) {
			interpretEOption(eval);
		}
		
		if (enableProfiling) {
			final boolean divInvs = divideByInvocations;
			
			List<Interpreter.ProfilerData> data = Interpreter.profilerData.values()
					.stream()
					.sorted((a, b) ->
						Long.compare(divInvs ? b.time / b.invocations : b.time, divInvs ? a.time / a.invocations : a.time))
					.collect(toList());
			
			long sum = data.stream().mapToLong(e -> e.time).sum();
			
			double acc = 0;
			
			System.out.printf("%5s %6s %6s %4s %s\n", "%", "ACC", "MS", "INVS", "FUNCTION");
			
			for (Interpreter.ProfilerData pd : data) {
				String f = pd.function;
				int invs = pd.invocations;
				double timenanos = pd.time;
				if (divideByInvocations) timenanos /= invs;
				double time = timenanos / 1_000_000d;
				double percent = 100d * timenanos / sum;
				acc += percent;
				
				System.out.printf("%5.2f %6.2f %6.2f %4d %s\n", percent, acc, time, invs, f);
			}
		}

		Interpreter.shutdown();
		STDIN.finish();
		STDOUT.finish();
		STDERR.finish();
	}
}
