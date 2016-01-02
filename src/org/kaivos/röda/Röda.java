package org.kaivos.röda;

import org.kaivos.röda.Interpreter;

import org.kaivos.nept.parser.ParsingException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileInputStream;

/**
 * A simple stream language
 */
public class Röda {
	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.err.println("Usage: röda file | röd -i");
			System.exit(1);
			return;
		}

		String file = args[0];

		if (file.equals("-i")) {

			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			Interpreter c = new Interpreter();
			String line = "";
			int i = 1;
			System.out.print("> ");
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
				System.out.print("> ");
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
			return;
		}
	}
}
