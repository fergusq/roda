package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.RödaValue.STRING;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import org.kaivos.röda.IOUtils;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class ReadAndWritePopulator {

	public static void populateReadAndWrite(Interpreter I, RödaScope S) {
		S.setLocal("cat", RödaNativeFunction.of("cat", (typeargs, args, scope, in, out) -> {
			if (args.size() < 1) argumentUnderflow("cat", 1, args.size());
			for (RödaValue value : args) {
				String filename = value.str();
				File file = IOUtils.getMaybeRelativeFile(I.currentDir,
									 filename);
				for (String line : IOUtils.fileIterator(file)) {
					out.push(RödaString.of(line));
				}
			}
		}, Arrays.asList(new Parameter("files", false, STRING)), true));
		
		S.setLocal("write", RödaNativeFunction.of("write", (typeargs, args, scope, in, out) -> {
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
				}, Arrays.asList(new Parameter("file", false, STRING)), false));
	}

}
