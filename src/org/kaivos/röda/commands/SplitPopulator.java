package org.kaivos.röda.commands;

import static java.util.stream.Collectors.toList;
import static org.kaivos.röda.Interpreter.checkString;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.RödaStream;
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
	
	public static void addSplitter(RödaScope S, String name, boolean spaceseparated, Splitter s) {
		S.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, scope, in, out) -> {
					String separator = " ";
					if (!spaceseparated) {
						RödaValue newSep = args.get(0);
						checkString(name, newSep);
						separator = newSep.str();
					}
					if (args.size() > (spaceseparated ? 0 : 1)) {
						for (int i = (spaceseparated ? 0 : 1); i < args.size(); i++) {
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
				}, spaceseparated ?
					Arrays.asList(new Parameter("strings", false)) :
					Arrays.asList(new Parameter("separator", false), new Parameter("strings", false)),
				true));
	}

	public static void populateSplit(RödaScope S) {
		addSplitter(S, "split", true, SplitPopulator::pushUncollectedSeparation);
		addSplitter(S, "splitAt", false, SplitPopulator::pushUncollectedSeparation);
		addSplitter(S, "splitAll", true, SplitPopulator::pushCollectedSeparation);
		addSplitter(S, "splitAllAt", false, SplitPopulator::pushCollectedSeparation);
	}
}
