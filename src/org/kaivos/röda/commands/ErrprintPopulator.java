package org.kaivos.röda.commands;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;

public final class ErrprintPopulator {

	private ErrprintPopulator() {}

	public static void populateErrprint(RödaScope S) {
		S.setLocal("errprint", RödaNativeFunction.of("errprint", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.isEmpty()) {
				while (true) {
					RödaValue input = in.pull();
					if (input == null) break;
					System.err.print(input.str());
				}
			} else
				for (RödaValue value : args) {
					System.err.print(value.str());
				}
		}, Arrays.asList(new Parameter("values", false)), true));
	}
}
