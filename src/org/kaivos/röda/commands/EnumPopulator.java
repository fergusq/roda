package org.kaivos.röda.commands;

import static org.kaivos.röda.Parser.expressionInt;
import static org.kaivos.röda.RödaValue.INTEGER;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class EnumPopulator {
	
	private EnumPopulator() {}

	public static void populateEnum(RödaScope S) {
		S.setLocal("enum", RödaNativeFunction.of("enum", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() > 1) Interpreter.argumentOverflow("enum", 1, args.size());
			long i = args.size() == 0 ? 0 : args.get(0).integer();
			long step = kwargs.get("step").integer();
			while (true) {
				RödaValue val = in.pull();
				if (val == null) break;
				out.push(val);
				out.push(RödaInteger.of(i));
				i += step;
			}
		}, Arrays.asList(new Parameter("fst", false, INTEGER)), true,
				Arrays.asList(new Parameter("step", false, expressionInt("<enum populator>", 0, 1)))));
	}
}
