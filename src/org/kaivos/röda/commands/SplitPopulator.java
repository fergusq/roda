package org.kaivos.röda.commands;

import static java.util.stream.Collectors.toList;
import static org.kaivos.röda.Interpreter.checkString;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaList;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class SplitPopulator {

	private SplitPopulator() {}

	public static void populateSplit(RödaScope S) {
		S.setLocal("split", RödaNativeFunction.of("split", (typeargs, args, scope, in, out) -> {
					String separator = " ";
					boolean streamInput = true;
					boolean collect = false;
					for (int i = 0; i < args.size(); i++) {
						RödaValue value = args.get(i);
						if (value.isFlag("-s")) {
							RödaValue newSep = args.get(++i);
							checkString("split", newSep);
							separator = newSep.str();
							continue;
						}
						else if (value.isFlag("-c")) {
							collect = true;
							continue;
						}
						streamInput = false;
						checkString("split", value);
						String str = value.str();
						if (!collect) {
							for (String s : str.split(separator)) {
								out.push(RödaString.of(s));
							}
						}
						else {
							out.push(RödaList.of(Arrays.asList(str.split(separator))
									     .stream().map(RödaString::of).collect(toList())));
						}
					}
					if (streamInput) {
						while (true) {
							RödaValue value = in.pull();
							if (value == null) break;
							
							checkString("split", value);
							String str = value.str();
							if (!collect) {
								for (String s : str.split(separator)) {
									out.push(RödaString.of(s));
								}
							}
							else {
								out.push(RödaList.of(Arrays.asList(str.split(separator))
									     .stream().map(RödaString::of).collect(toList())));
							}
						}
					}
				}, Arrays.asList(new Parameter("flags_and_strings", false)), true));
	}
}
