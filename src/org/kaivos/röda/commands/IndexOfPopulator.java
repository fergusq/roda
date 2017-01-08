package org.kaivos.röda.commands;

import static org.kaivos.röda.RödaValue.STRING;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class IndexOfPopulator {
	
	private IndexOfPopulator() {}

	public static void populateIndexOf(RödaScope S) {
		S.setLocal("indexOf", RödaNativeFunction.of("indexOf", (typeargs, args, kwargs, scope, in, out) -> {
			out.push(RödaInteger.of(args.get(1).str().indexOf(args.get(0).str())));
		}, Arrays.asList(new Parameter("sequence", true, STRING), new Parameter("str", false, STRING)), true));
	}
}
