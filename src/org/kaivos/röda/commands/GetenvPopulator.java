package org.kaivos.röda.commands;

import static org.kaivos.röda.RödaValue.STRING;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class GetenvPopulator {

	private GetenvPopulator() {}

	public static void populateGetenv(RödaScope S) {
		S.setLocal("getenv", RödaNativeFunction.of("getenv", (typeargs, args, kwargs, scope, in, out) -> {
			out.push(RödaString.of(System.getenv(args.get(0).str())));
		}, Arrays.asList(new Parameter("name", false, STRING)), false));
	}
}
