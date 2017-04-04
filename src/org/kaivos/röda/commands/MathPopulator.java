package org.kaivos.röda.commands;

import static org.kaivos.röda.RödaValue.FLOATING;
import static org.kaivos.röda.RödaValue.INTEGER;
import static org.kaivos.röda.RödaValue.NUMBER;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaFloating;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class MathPopulator {
	
	private MathPopulator() {}

	public static void populateMath(RödaScope S) {
		S.setLocal("abs", RödaNativeFunction.of("abs", (typeargs, args, kwargs, scope, in, out) -> {
			RödaValue arg = args.get(0);
			if (arg.is(INTEGER)) out.push(RödaInteger.of(Math.abs(arg.integer())));
			else if (arg.is(FLOATING)) out.push(RödaFloating.of(Math.abs(arg.floating())));
		}, Arrays.asList(new Parameter("variables", false, NUMBER)), false));
	}
}
