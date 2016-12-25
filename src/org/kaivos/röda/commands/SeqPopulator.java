package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.RödaValue.INTEGER;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import static org.kaivos.röda.Parser.expressionInt;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class SeqPopulator {

	private SeqPopulator() {}

	public static void populateSeq(RödaScope S) {
		S.setLocal("seq", RödaNativeFunction.of("seq", (typeargs, args, kwargs, scope, in, out) -> {
			long from = args.get(0).integer();
			long to = args.get(1).integer();
			long step = kwargs.get("step").integer();
			if (step > 0) {
				for (long i = from; i <= to; i += step)
					out.push(RödaInteger.of(i));
			}
			else if (step < 0) {
				for (long i = from; i >= to; i += step)
					out.push(RödaInteger.of(i));
			}
			else {
				error("illegal use of seq: step must be non-zero");
			}
		}, Arrays.asList(new Parameter("from", false, INTEGER), new Parameter("to", false, INTEGER)), false,
				Arrays.asList(new Parameter("step", false, expressionInt("<seq populator>", 0, 1)))));
	}
}
