package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.outOfBounds;
import static org.kaivos.röda.Parser.expressionInt;
import static org.kaivos.röda.RödaValue.BOOLEAN;
import static org.kaivos.röda.RödaValue.INTEGER;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class SeqPopulator {

	private SeqPopulator() {}

	public static void populateSeq(RödaScope S) {
		S.setLocal("seq", RödaNativeFunction.of("seq", (typeargs, args, kwargs, scope, in, out) -> {
			long from = args.get(0).integer();
			long to = args.get(1).integer();
			long step = kwargs.get("step").integer();
			if (to < from && step > 0 || from < to && step < 0) step = -step;
			if (step > 0) {
				for (long i = from; i <= to; i += step)
					out.push(RödaInteger.of(i));
			}
			else if (step < 0) {
				for (long i = from; i >= to; i += step)
					out.push(RödaInteger.of(i));
			}
			else {
				outOfBounds("illegal use of seq: step must be non-zero");
			}
		}, Arrays.asList(new Parameter("from", false, INTEGER), new Parameter("to", false, INTEGER)), false,
				Arrays.asList(new Parameter("step", false, expressionInt("<seq populator>", 0, 1)))));
		
		S.setLocal("makeSeq", RödaNativeFunction.of("makeSeq", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.get(0).bool()) {
				out.push(args.get(args.size()-1));
				for (int i = args.size()-1; i >= 1; i--) {
					in.unpull(args.get(i));
				}
			}
		}, Arrays.asList(new Parameter("cond", false, BOOLEAN), new Parameter("first_value", false), new Parameter("other_values", false)), true));
	}
}
