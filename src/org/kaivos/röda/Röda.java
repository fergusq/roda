package org.kaivos.röda;

import static java.util.stream.Collectors.toList;
import static org.kaivos.röda.Interpreter.INTERPRETER;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.kaivos.nept.parser.ParsingException;
import org.kaivos.röda.Interpreter.RödaException;
import org.kaivos.röda.RödaStream.ISLineStream;
import org.kaivos.röda.RödaStream.OSStream;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

import jline.console.ConsoleReader;
import jline.console.history.FileHistory;

/**
 * A simple stream language
 */
public class Röda {
	
	public static final String RÖDA_VERSION_STRING = "0.12-alpha";
	
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
	
	private static RödaStream STDIN, STDOUT;
	static {
		InputStreamReader ir = new InputStreamReader(System.in);
		BufferedReader in = new BufferedReader(ir);
		PrintWriter out = new PrintWriter(System.out);
		STDIN = new ISLineStream(in);
		STDOUT = new OSStream(out);
	}
	
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
				enableDebug = true, enableProfiling = false, divideByInvocations = false;
		String prompt = null;
		
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
		interpretEOption(eval);

		if (file != null) {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
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
				INTERPRETER.interpret(code, valueArgs, file, STDIN, STDOUT);
			} catch (ParsingException e) {
				System.err.println("[E] " + e.getMessage());
			} catch (Interpreter.RödaException e) {
				printRödaException(e);
			}
		} else if (!disableInteraction && interactive && System.console() != null) {

			File historyFile = new File(System.getProperty("user.home") + "/.rödahist");

			FileHistory history = new FileHistory(historyFile);

			ConsoleReader in = new ConsoleReader();
			in.setExpandEvents(false);
			in.setHistory(history);
			in.setPrompt(prompt);

			PrintWriter out = new PrintWriter(in.getOutput());

			RödaStream inStream = RödaStream.makeEmptyStream(), 
					outStream = new OSStream(out);

			INTERPRETER.G.setLocal("prompt", RödaNativeFunction.of("prompt", (ta, a, k, s, i, o) -> {
						Interpreter.checkString("prompt", a.get(0));
						in.setPrompt(a.get(0).str());
					}, Arrays.asList(new Parameter("prompt_string", false)), false));

			INTERPRETER.G.setLocal("getLine", RödaNativeFunction.of("getLine", (ta, a, k, s, i, o) -> {
						try {
							o.push(RödaString.of(in.readLine("? ")));
						} catch (IOException e) {
							Interpreter.error(e);
						}
					}, Arrays.asList(), false));
			
			in.addCompleter((b, k, l) -> {
					if (b == null) l.addAll(INTERPRETER.G.map.keySet());
					else {
						int i = Math.max(b.lastIndexOf(" "), Math.max(b.lastIndexOf("|"), Math.max(b.lastIndexOf("{"), b.lastIndexOf(";"))))+1;
						String a = b.substring(0, i);
						b = b.substring(i);
						TreeSet<String> vars = new TreeSet<>(INTERPRETER.G.map.keySet());
						for (String match : vars.tailSet(b)) {
							if (!match.startsWith(b)) break;
							l.add(a + match + " ");
						}
					}
					return l.isEmpty() ? -1 : 0;
				});
			String line = "";
			int i = 1;
			while ((line = in.readLine()) != null) {
				if (!line.trim().isEmpty()) {
					try {
						INTERPRETER.interpretStatement(line, "<line "+ i++ +">", inStream, outStream);
					} catch (ParsingException e) {
						out.println("[E] " + e.getMessage());
					} catch (Interpreter.RödaException e) {
						printRödaException(e);
					}
				}
			}

			history.flush();

			System.out.println();

		} else if (!disableInteraction) {

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
	}
}
