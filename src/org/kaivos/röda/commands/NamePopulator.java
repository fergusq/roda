package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.typeMismatch;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class NamePopulator {
	
	private NamePopulator() {}

	public static void populateName(RödaScope S) {
		S.setLocal("name", RödaNativeFunction.of("name", (typeargs, args, kwargs, scope, in, out) -> {
			for (RödaValue value : args) {
				if (!value.is(RödaValue.REFERENCE))
					typeMismatch("invalid argument for undefine: only references accepted");

				out.push(RödaString.of(value.target()));
			}
		}, Arrays.asList(new Parameter("variables", true)), true));
	}
}
