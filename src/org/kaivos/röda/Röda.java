package org.kaivos.röda;

import org.kaivos.röda.Interpreter;
import static org.kaivos.röda.RödaStream.OSStream;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.type.RödaString;
import org.kaivos.röda.type.RödaNativeFunction;

import org.kaivos.nept.parser.ParsingException;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;

import java.util.List;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Arrays;

import java.util.stream.Collectors;

import jline.console.ConsoleReader;
import jline.console.history.FileHistory;

/**
 * A simple stream language
 */
public class Röda {
	public static void main(String[] args) throws IOException {
		String file = null;
		List<String> argsForRöda = new ArrayList<>();
		boolean interactive = System.console() != null, forcedI = false;
		String prompt = null;
		
		for (int i = 0; i < args.length; i++) {
			if (file != null) argsForRöda.add(args[i]);
			switch (args[i]) {
			case "-p":
				prompt = args[++i];
				continue;
			case "-P":
				prompt = "";
				continue;
			case "-i":
				interactive = true;
				forcedI = true;
				continue;
			case "-I":
				interactive = false;
				continue;
			case "-h":
			case "--help": {
				System.out.println("Usage: röda [options] file | röda [options] -i | röda [options]");
				System.out.println("Available options:");
				System.out.println("-p prompt  Change the prompt in interactive mode");
				System.out.println("-P         Disable prompt in interactive mode");
				System.out.println("-i         Enable interactive mode");
                                System.out.println("-I         Disable interactive mode");
				System.out.println("-h, --help Show this help text");
				return;
			}
			default:
				file = args[i];
				continue;
			}
		}

		if (prompt == null) prompt = interactive ? "! " : "";

		if (file != null && forcedI) {
			System.err.println("Usage: röda [options] file | röda [options] -i | röda [options]");
			System.exit(1);
			return;
		}

		if (file != null) {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			String code = "";
			String line = "";
			while ((line = in.readLine()) != null) {
				code += line + "\n";
			}
			in.close();
			Interpreter c = new Interpreter();
			List<RödaValue> valueArgs = argsForRöda.stream()
				.map(RödaString::of)
				.collect(Collectors.toList());
			try {
				c.interpret(code, valueArgs, file);
			} catch (ParsingException e) {
				System.err.println("[E] " + e.getMessage());
			} catch (Interpreter.RödaException e) {
				System.err.println("[E] " + e.getMessage());
				for (String step : e.getStack()) {
					System.err.println(step);
				}
				if (e.getCause() != null) e.getCause().printStackTrace();
			}
		} else if (interactive && System.console() != null) {

			File historyFile = new File(System.getProperty("user.home") + "/.rödahist");

			FileHistory history = new FileHistory(historyFile);

			ConsoleReader in = new ConsoleReader();
			in.setExpandEvents(false);
			in.setHistory(history);
			in.setPrompt(prompt);

			PrintWriter out = new PrintWriter(in.getOutput());

			Interpreter c = new Interpreter(RödaStream.makeEmptyStream(),
							new OSStream(out));

			c.G.setLocal("prompt", RödaNativeFunction.of("prompt", (ta, a, s, i, o) -> {
						Interpreter.checkString("prompt", a.get(0));
						in.setPrompt(a.get(0).str());
					}, Arrays.asList(new Parameter("prompt_string", false)), false));
			
			in.addCompleter((b, k, l) -> {
					if (b == null) l.addAll(c.G.map.keySet());
					else {
						int i = Math.max(b.lastIndexOf(" "), Math.max(b.lastIndexOf("|"), Math.max(b.lastIndexOf("{"), b.lastIndexOf(";"))))+1;
						String a = b.substring(0, i);
						b = b.substring(i);
						TreeSet<String> vars = new TreeSet<>(c.G.map.keySet());
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
						c.interpretStatement(line, "<line "+ i++ +">");
					} catch (ParsingException e) {
						out.println("[E] " + e.getMessage());
					} catch (Interpreter.RödaException e) {
						out.println("[E] " + e.getMessage());
						for (String step : e.getStack()) {
							out.println(step);
						}
						if (e.getCause() != null) e.getCause().printStackTrace();
					}
				}
			}

			history.flush();

			System.out.println();

		} else {

			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			Interpreter c = new Interpreter();
			String line = "";
			int i = 1;
			System.out.print(prompt);
			while ((line = in.readLine()) != null) {
				if (!line.trim().isEmpty()) {
					try {
						c.interpretStatement(line, "<line "+ i++ +">");
					} catch (ParsingException e) {
						System.out.println("[E] " + e.getMessage());
					} catch (Interpreter.RödaException e) {
						System.out.println("[E] " + e.getMessage());
						for (String step : e.getStack()) {
							System.out.println(step);
						}
						if (e.getCause() != null) e.getCause().printStackTrace();
					}
				}
				System.out.print(prompt);
			}
		}

		Interpreter.shutdown();
	}
}
