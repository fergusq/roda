package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.RödaValue.STRING;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.kaivos.röda.IOUtils;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class CdAndPwdPopulator {

	private CdAndPwdPopulator() {}

	public static void populateCdAndPwd(Interpreter I, RödaScope S) {
		S.setLocal("cd", RödaNativeFunction.of("cd", (typeargs, args, kwargs, scope, in, out) -> {
			String dirname = args.get(0).str();
			File dir = IOUtils.getMaybeRelativeFile(I.currentDir, dirname);
			if (!dir.isDirectory()) {
				error("cd: not a directory");
			}
			I.currentDir = dir;
		}, Arrays.asList(new Parameter("path", false, STRING)), false));
		
		S.setLocal("pwd", RödaNativeFunction.of("pwd", (typeargs, args, kwargs, scope, in, out) -> {
			try {
				out.push(RödaString.of(I.currentDir.getCanonicalPath()));
			} catch (IOException e) {
				error(e);
			}
		}, Arrays.asList(), false));
	}
}
