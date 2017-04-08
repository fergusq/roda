package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkArgs;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Interpreter.typeMismatch;
import static org.kaivos.röda.RödaValue.STRING;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;

public class ErrorPopulator {

	private ErrorPopulator() {}

	public static void populateError(RödaScope S) {
		S.setLocal("error", RödaNativeFunction.of("error", (typeargs, args, kwargs, scope, in, out) -> {
			checkArgs("error", 1, args.size());
			if (args.get(0).is(STRING)) {
				error(args.get(0).str());
			} else if (!args.get(0).is("Error")) {
				typeMismatch("error: can't cast " + args.get(0).typeString() + " to an error");
			} else
				error(args.get(0));
		}, Arrays.asList(new Parameter("errorObject", false)), false));
	}
}
