package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.RödaValue.STRING;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class SearchPopulator {

	private SearchPopulator() {}

	public static void populateSearch(RödaScope S) {
		S.setLocal("search", RödaNativeFunction.of("search", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() < 1)
				argumentUnderflow("search", 1, 0);
			while (true) {
				RödaValue input = in.pull();
				if (input == null) break;

				String text = input.str();
				for (RödaValue value : args) {
					checkString("search", value);
					Pattern pattern = Pattern.compile(value.str());
					Matcher m = pattern.matcher(text);
					while (m.find()) {
						out.push(RödaString.of(m.group()));
					}
				}
			}
		}, Arrays.asList(new Parameter("patterns", false, STRING)), true));
	}
}
