package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.RödaValue.STRING;

import java.io.File;
import java.util.Arrays;

import org.kaivos.röda.IOUtils;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaNativeFunction;

public final class ImportPopulator {
	
	private ImportPopulator() {}

	public static void populateImport(Interpreter I, RödaScope S) {
		S.setLocal("import", RödaNativeFunction.of("import", (typeargs, args, scope, in, out) -> {
				        for (RödaValue value : args) {
						checkString("import", value);
						String filename = value.str();
						File file = IOUtils.getMaybeRelativeFile(I.currentDir,
											 filename);
						I.loadFile(file, I.G);
					}
				}, Arrays.asList(new Parameter("files", false, STRING)), true));
	}
}
