package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.RödaValue.STRING;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.stream.Stream;

import org.kaivos.röda.IOUtils;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Parser;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class ReadAndWritePopulator {

	public static void populateReadAndWrite(Interpreter I, RödaScope S) {
		S.setLocal("readLines", RödaNativeFunction.of("readLines", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() < 1) argumentUnderflow("readLines", 1, args.size());
			long skip = kwargs.get("skip").integer();
			long limit = kwargs.get("limit").integer();
			for (RödaValue value : args) {
				String filename = value.str();
				File file = IOUtils.getMaybeRelativeFile(I.currentDir, filename);
				try {
					Stream<String> stream = Files.lines(file.toPath()).skip(skip);
					if (limit >= 0) stream = stream.limit(limit);
					stream.map(RödaString::of).forEach(out::push);
				} catch (IOException e) {
					error(e);
				}
			}
		}, Arrays.asList(new Parameter("files", false, STRING)), true,
				Arrays.asList(
						new Parameter("skip", false, Parser.expressionInt("<read and write populator>", 0, 0)),
						new Parameter("limit", false, Parser.expressionInt("<read and write populator>", 0, -1))
				)));
		
		S.setLocal("writeStrings", RödaNativeFunction.of("writeStrings", (typeargs, args, kwargs, scope, in, out) -> {
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
