package org.kaivos.röda;

import org.kaivos.röda.Interpreter;

import org.kaivos.nept.parser.ParsingException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileInputStream;

import java.util.List;
import java.util.ArrayList;

import java.util.stream.Collectors;

/**
 * A simple stream language
 */
public class Röda {
	public static void main(String[] args) throws IOException {
		String file = null;
		List<String> argsForRöda = new ArrayList<>();
		boolean interactive = false;
		String prompt = "> ";
		
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
				continue;
			case "-h":
			case "--help": {
				System.out.println("Usage: röda [options] file | röda [options] -i");
				System.out.println("Available options:");
				System.out.println("-p prompt  Change the prompt in interactive mode");
				System.out.println("-P         Disable prompt in interactive mode");
				System.out.println("-i         Enable interactive mode");
				System.out.println("-h, --help Show this help text");
				
			} continue;
			default:
				file = args[i];
				continue;
			}
		}
		
		if (file == null ^ interactive) {
			System.err.println("Usage: röda file | röda [-p prompt] -i");
			System.exit(1);
			return;
		}

		if (interactive) {

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

			return;
		} else {
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
		}
	}
}
