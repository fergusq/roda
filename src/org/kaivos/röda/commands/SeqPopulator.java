package org.kaivos.röda.commands;

import static org.kaivos.röda.RödaValue.INTEGER;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class SeqPopulator {

	private SeqPopulator() {}

	public static void populateSeq(RödaScope S) {
		S.setLocal("seq", RödaNativeFunction.of("seq", (typeargs, args, scope, in, out) -> {
					long from = args.get(0).integer();
					long to = args.get(1).integer();
					for (long i = from; i <= to; i++) out.push(RödaInteger.of(i));
				}, Arrays.asList(new Parameter("from", false, INTEGER),
						 new Parameter("to", false, INTEGER)), false));
	}
}
