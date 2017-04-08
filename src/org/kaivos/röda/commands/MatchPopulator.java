package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.RödaValue.STRING;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaList;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class MatchPopulator {

	private MatchPopulator() {}

	public static void populateMatch(RödaScope S) {
		S.setLocal("match", RödaNativeFunction.of("match", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() < 1)
				argumentUnderflow("match", 1, 0);
			String regex = args.get(0).str();
			args.remove(0);
			Pattern pattern;
			try {
				pattern = Pattern.compile(regex);
			} catch (PatternSyntaxException e) {
				error("match: pattern syntax exception: " + e.getMessage());
				return;
			}

			if (args.size() > 0) {
				for (RödaValue arg : args) {
					Matcher matcher = pattern.matcher(arg.str());
					if (matcher.matches()) {
						RödaValue[] results = new RödaValue[matcher.groupCount() + 1];
						for (int i = 0; i < results.length; i++) {
							String group = matcher.group(i);
							results[i] = RödaString.of(group != null ? group : "");
						}
						out.push(RödaList.of(results));
					} else
						out.push(RödaList.of());
				}
			} else {
				while (true) {
					RödaValue input = in.pull();
					if (input == null)
						break;
					checkString("match", input);
					Matcher matcher = pattern.matcher(input.str());
					if (matcher.matches()) {
						RödaValue[] results = new RödaValue[matcher.groupCount()];
						for (int i = 0; i < results.length; i++) {
							String group = matcher.group(i);
							results[i] = RödaString.of(group != null ? group : "");
						}
						out.push(RödaList.of(results));
					} else
						out.push(RödaList.of());
				}
			}
		}, Arrays.asList(new Parameter("pattern", false, STRING), new Parameter("strings", false, STRING)), true));
	}
}
