package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkFlag;
import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.Interpreter.error;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import org.kaivos.röda.IOUtils;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaBoolean;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class FilePopulator {

	private FilePopulator() {}

	public static void populateFile(Interpreter I, RödaScope S) {
		S.setLocal("file", RödaNativeFunction.of("file", (typeargs, args, scope, in, out) -> {
					if (args.size() < 1) argumentUnderflow("file", 1, args.size());
					for (int i = 0; i < args.size(); i++) {
						RödaValue flag = args.get(i);
						checkFlag("file", flag);
						RödaValue value = args.get(++i);
						checkString("file", value);
						String filename = value.str();
						File file = IOUtils.getMaybeRelativeFile(I.currentDir,
											 filename);
						if (flag.isFlag("-l"))
							out.push(RödaInteger.of(file.length()));
						else if (flag.isFlag("-e"))
							out.push(RödaBoolean.of(file.exists()));
						else if (flag.isFlag("-f"))
							out.push(RödaBoolean.of(file.isFile()));
						else if (flag.isFlag("-d"))
							out.push(RödaBoolean.of(file.isDirectory()));
						else if (flag.isFlag("-m")) try {
								out.push(RödaString
									 .of(Files
									     .probeContentType(file.toPath())));
							} catch (IOException e) { error(e); }
						else error("unknown command " + flag.str());
					}
				}, Arrays.asList(new Parameter("commands_and_files", false)), true));
	}
}
