package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkReference;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;

public final class UndefinePopulator {
	
	private UndefinePopulator() {}

	public static void populateUndefine(RödaScope S) {
		S.setLocal("undefine", RödaNativeFunction.of("undefine", (typeargs, args, kwargs, scope, in, out) -> {
			for (RödaValue value : args) {
				checkReference("undefine", value);

				value.assign(null);
			}
		}, Arrays.asList(new Parameter("variables", true)), true));
	}
}
