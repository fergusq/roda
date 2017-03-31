package org.kaivos.röda.commands;

import static java.util.stream.Collectors.toList;
import static org.kaivos.röda.Interpreter.checkString;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser;
import org.kaivos.röda.RödaStream;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaList;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class SplitPopulator {

	private SplitPopulator() {}
	
	private static interface Splitter {
		void pushSeparation(String str, String separator, RödaStream out);
	}
	
	private static void pushCollectedSeparation(String str, String separator, RödaStream out) {
		out.push(RödaList.of(Arrays.asList(str.split(separator)).stream().map(RödaString::of).collect(toList())));
	}
	
	private static void pushUncollectedSeparation(String str, String separator, RödaStream out) {
		for (String s : str.split(separator)) {
			out.push(RödaString.of(s));
		}
	}
	
	public static void addSplitter(RödaScope S, String name, Splitter s) {
		S.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
					String separator = kwargs.get("sep").str();
					if (args.size() > 0) {
						for (int i = 0; i < args.size(); i++) {
							RödaValue value = args.get(i);
							checkString(name, value);
							String str = value.str();
							s.pushSeparation(str, separator, out);
						}
					}
					else {
						while (true) {
							RödaValue value = in.pull();
							if (value == null) break;
							
							checkString(name, value);
							String str = value.str();
							s.pushSeparation(str, separator, out);
						}
					}
				},
				Arrays.asList(new Parameter("strings", false)),
				true,
				Arrays.asList(
						new Parameter("sep", false, Parser.expressionString("<split populator>", 0, " "))
						)));
	}

	public static void populateSplit(RödaScope S) {
		addSplitter(S, "split", SplitPopulator::pushUncollectedSeparation);
		addSplitter(S, "splitMany", SplitPopulator::pushCollectedSeparation);
		S.setLocal("chars", RödaNativeFunction.of("chars", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() > 0) {
				for (int i = 0; i < args.size(); i++) {
					RödaValue value = args.get(i);
					checkString("chars", value);
					String str = value.str();
					str.chars().mapToObj(c -> RödaString.of(new String(Character.toChars(c)))).forEach(out::push);;
				}
			}
			else {
				while (true) {
					RödaValue value = in.pull();
					if (value == null) break;
					
					checkString("chars", value);
					String str = value.str();
					str.chars().mapToObj(c -> RödaString.of(new String(Character.toChars(c)))).forEach(out::push);;
				}
			}
		},
		Arrays.asList(new Parameter("strings", false)),
		true));
	}
}
