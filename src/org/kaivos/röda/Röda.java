package org.kaivos.röda;

import org.kaivos.röda.Interpreter;

import org.kaivos.nept.parser.ParsingException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileInputStream;

import java.util.List;
import java.util.ArrayList;

/**
 * A simple stream language
 */
public class Röda {
	public static void main(String[] args) throws IOException {
		List<String> files = new ArrayList<>();
		boolean interactive = false;
		String prompt = "> ";
		
		for (int i = 0; i < args.length; i++) {
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
				files.add(args[i]);
				continue;
			}
		}
		
		if (files.isEmpty() ^ interactive) {
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
		} else for (String file : files) {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			String code = "";
			String line = "";
			while ((line = in.readLine()) != null) {
				code += line + "\n";
			}
			in.close();
			Interpreter c = new Interpreter();
			try {
				c.interpret(code, file);
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
