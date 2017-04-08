package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Interpreter.illegalArguments;
import static org.kaivos.röda.RödaValue.STRING;

import java.util.Arrays;
import java.util.regex.PatternSyntaxException;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class ReplacePopulator {

	private ReplacePopulator() {}

	public static void populateReplace(RödaScope S) {
		S.setLocal("replace", RödaNativeFunction.of("replace", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() % 2 != 0)
				illegalArguments("invalid arguments for replace: even number required (got " + args.size() + ")");
			try {
				while (true) {
					RödaValue input = in.pull();
					if (input == null) break;

					String text = input.str();
					for (int i = 0; i < args.size(); i += 2) {
						String pattern = args.get(i).str();
						String replacement = args.get(i + 1).str();
						text = text.replaceAll(pattern, replacement);
					}
					out.push(RödaString.of(text));
				}
			} catch (PatternSyntaxException e) {
				error("replace: pattern syntax exception: " + e.getMessage());
			}
		}, Arrays.asList(new Parameter("patterns_and_replacements", false, STRING)), true));
	}
}
