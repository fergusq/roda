package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkInteger;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Interpreter.outOfBounds;
import static org.kaivos.röda.RödaValue.STRING;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaFloating;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class ParseNumPopulator {

	private ParseNumPopulator() {}

	public static void populateParseNum(RödaScope S) {
		S.setLocal("parseInteger", RödaNativeFunction.of("parseInteger", (typeargs, args, kwargs, scope, in, out) -> {
			checkInteger("parseInteger", kwargs.get("radix"));
			long radixl = kwargs.get("radix").integer();
			if (radixl < Character.MIN_RADIX || radixl > Character.MAX_RADIX)
				outOfBounds("parseInteger: radix out of bounds: " + radixl);
			int radix = (int) radixl;
			if (args.isEmpty()) {
				argumentUnderflow("parseInteger", 1, 0);
			}
			try {
				for (RödaValue v : args) {
					out.push(RödaInteger.of(Long.parseLong(v.str(), radix)));
				}
			} catch (NumberFormatException e) {
				error("number format error: " + e.getMessage());
			}
		}, Arrays.asList(new Parameter("strings", false, STRING)), true,
				Arrays.asList(new Parameter("radix", false, Parser.expressionInt("<parseInteger populator>", 0, 10)))));
		
		S.setLocal("parseFloating", RödaNativeFunction.of("parseFloating", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.isEmpty()) {
				argumentUnderflow("parseFloating", 1, 0);
			}
			try {
				for (RödaValue v : args) {
					out.push(RödaFloating.of(Double.parseDouble(v.str())));
				}
			} catch (NumberFormatException e) {
				error("number format error: " + e.getMessage());
			}
		}, Arrays.asList(new Parameter("strings", false, STRING)), true));
	}
}
