package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.illegalArguments;
import static org.kaivos.röda.Interpreter.outOfBounds;
import static org.kaivos.röda.RödaValue.INTEGER;
import static org.kaivos.röda.RödaValue.STRING;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public class ChrAndOrdPopulator {

	private ChrAndOrdPopulator() {}
	
	public static void populateChrAndOrd(RödaScope S) {
		S.setLocal("chr", RödaNativeFunction.of("chr", (typeargs, args, kwargs, scope, in, out) -> {
			long arg = args.get(0).integer();
			if (arg < 0 || arg > Integer.MAX_VALUE) {
				outOfBounds("chr: code point out of range: " + arg);
			}
			out.push(RödaString.of(new String(Character.toChars((int) arg))));
		}, Arrays.asList(new Parameter("n", false, INTEGER)), false));
		
		S.setLocal("ord", RödaNativeFunction.of("ord", (typeargs, args, kwargs, scope, in, out) -> {
			String arg = args.get(0).str();
			int length = arg.codePointCount(0, arg.length());
			if (length > 1) {
				illegalArguments("ord: expected only one character, got " + length);
			}
			out.push(RödaInteger.of(arg.codePointAt(0)));
		}, Arrays.asList(new Parameter("c", false, STRING)), false));
	}
	
}
