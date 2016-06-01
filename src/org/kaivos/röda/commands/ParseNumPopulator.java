package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkNumber;
import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.Interpreter.error;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class ParseNumPopulator {

	private ParseNumPopulator() {}

	public static void populateParseNum(RödaScope S) {
		S.setLocal("parse_num", RödaNativeFunction.of("parse_num", (typeargs, args, scope, in, out) -> {
					int radix = 10;
					boolean tochr = false;
					while (args.size() > 0 && args.get(0).is(RödaValue.FLAG)) {
						String flag = args.remove(0).str();
						switch (flag) {
						case "-r":
							if (args.size() == 0)
								argumentUnderflow("parse_num", 1, args.size());
							checkNumber("parse_num", args.get(0));
							long radixl = args.remove(0).integer();
							if (radixl > Integer.MAX_VALUE)
								error("parse_num: radix too great: " + radixl);
							radix = (int) radixl;
							break;
						case "-c":
							tochr = true;
						}
					}
					if (args.size() > 0) {
						for (RödaValue v : args) {
							checkString("parse_num", v);
							long lng = Long.parseLong(v.str(), radix);
							if (tochr) out.push(RödaString
									    .of(String.valueOf((char) lng)));
							else out.push(RödaInteger.of(lng));
						}
					} else {
						while (true) {
							RödaValue v = in.pull();
							if (v == null) break;
							checkString("parse_num", v);
							long lng = Long.parseLong(v.str(), radix);
							if (tochr) out.push(RödaString
									    .of(String.valueOf((char) lng)));
							else out.push(RödaInteger.of(lng));
						}
					}
				}, Arrays.asList(new Parameter("strings", false)), true));
	}
}
