package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkNumber;
import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.Interpreter.error;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class ParseNumPopulator {

	private ParseNumPopulator() {}

	public static void populateParseNum(RödaScope S) {
		S.setLocal("parseInteger", RödaNativeFunction.of("parseInteger", (typeargs, args, kwargs, scope, in, out) -> {
			checkNumber("parseInteger", kwargs.get("radix"));
			long radixl = kwargs.get("radix").integer();
			if (radixl > Integer.MAX_VALUE)
				error("parseInteger: radix too great: " + radixl);
			int radix = (int) radixl;
			if (args.size() > 0) {
				for (RödaValue v : args) {
					checkString("parseInteger", v);
					long lng = Long.parseLong(v.str(), radix);
					out.push(RödaInteger.of(lng));
				}
			} else {
				while (true) {
					RödaValue v = in.pull();
					if (v == null)
						break;
					checkString("parseInteger", v);
					long lng = Long.parseLong(v.str(), radix);
					out.push(RödaInteger.of(lng));
				}
			}
		}, Arrays.asList(new Parameter("strings", false)), true,
				Arrays.asList(new Parameter("radix", false, Parser.expressionInt("<parseInteger populator>", 0, 10)))));
	}
}
