package org.kaivos.röda;

import org.kaivos.röda.Interpreter;
import org.kaivos.röda.RödaStream.ISLineStream;
import org.kaivos.röda.RödaStream.OSStream;

import org.kaivos.nept.parser.ParsingException;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileInputStream;

import java.util.List;
import java.util.ArrayList;
import java.util.TreeSet;

import java.util.stream.Collectors;

import jline.console.ConsoleReader;

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
				.map(RödaValue::valueFromString)
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

			ConsoleReader in = new ConsoleReader();
			in.setExpandEvents(false);
			in.setPrompt(prompt);

			PrintWriter out = new PrintWriter(in.getOutput());

			Interpreter c = new Interpreter(new ISLineStream(new BufferedReader(new InputStreamReader(in.getInput()))),
							new OSStream(out));
			in.addCompleter((b, k, l) -> {
					if (b == null) l.addAll(c.G.map.keySet());
					else {
						TreeSet<String> vars = new TreeSet<>(c.G.map.keySet());
						for (String match : vars.tailSet(b)) {
							if (!match.startsWith(b)) break;
							l.add(match + " ");
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
