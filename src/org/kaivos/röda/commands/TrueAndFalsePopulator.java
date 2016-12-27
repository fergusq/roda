package org.kaivos.röda.commands;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.type.RödaBoolean;
import org.kaivos.röda.type.RödaNativeFunction;

public final class TrueAndFalsePopulator {

	private TrueAndFalsePopulator() {}

	public static void populateTrueAndFalse(RödaScope S) {
		S.setLocal("true", RödaNativeFunction.of("true", (typeargs, args, kwargs, scope, in, out) -> {
			out.push(RödaBoolean.of(true));
		}, Arrays.asList(), false));
	
		S.setLocal("false", RödaNativeFunction.of("false", (typeargs, args, kwargs, scope, in, out) -> {
			out.push(RödaBoolean.of(false));
		}, Arrays.asList(), false));
		
		S.setLocal("TRUE", RödaBoolean.of(true));
		S.setLocal("FALSE", RödaBoolean.of(false));
	}
}
