package org.kaivos.röda.commands;

import static org.kaivos.röda.RödaValue.STRING;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public class CasePopulator {

	private CasePopulator() {}
	
	public static void populateUpperAndLowerCase(RödaScope S) {
		S.setLocal("upperCase", RödaNativeFunction.of("upperCase", (typeargs, args, kwargs, scope, in, out) -> {
			out.push(RödaString.of(args.get(0).str().toUpperCase()));
		}, Arrays.asList(new Parameter("str", false, STRING)), false));
		S.setLocal("lowerCase", RödaNativeFunction.of("lowerCase", (typeargs, args, kwargs, scope, in, out) -> {
			out.push(RödaString.of(args.get(0).str().toLowerCase()));
		}, Arrays.asList(new Parameter("str", false, STRING)), false));
	}
	
}
