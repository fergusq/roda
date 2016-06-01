package org.kaivos.röda.commands;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class CurrentTimePopulator {

	private CurrentTimePopulator() {}

	public static void populateTime(RödaScope S) {
		S.setLocal("currentTime", RödaNativeFunction.of("currentTime", (typeargs, args, scope, in, out) -> {
			out.push(RödaInteger.of((int) System.currentTimeMillis()));
		}, Arrays.asList(), false));
	}
}
