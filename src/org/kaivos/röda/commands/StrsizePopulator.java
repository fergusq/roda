package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.RödaValue.STRING;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class StrsizePopulator {

	private StrsizePopulator() {}

	public static void populateStrsize(RödaScope S) {
		S.setLocal("strsize", RödaNativeFunction.of("strsize", (typeargs, args, kwargs, scope, in, out) -> {
			Charset chrset = StandardCharsets.UTF_8;
			Consumer<RödaValue> convert = v -> {
				checkString("strsize", v);
				out.push(RödaInteger.of(v.str().getBytes(chrset).length));
			};
			if (args.size() > 0) {
				args.forEach(convert);
			} else {
				in.forAll(convert);
			}
		}, Arrays.asList(new Parameter("strings", false, STRING)), true));
	}
}
