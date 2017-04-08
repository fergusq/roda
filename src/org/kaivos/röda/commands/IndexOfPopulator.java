package org.kaivos.röda.commands;

import static org.kaivos.röda.RödaValue.STRING;
import static org.kaivos.röda.Interpreter.checkList;
import static org.kaivos.röda.Interpreter.checkString;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class IndexOfPopulator {
	
	private IndexOfPopulator() {}

	public static void populateIndexOf(RödaScope S) {
		S.setLocal("indexOf", RödaNativeFunction.of("indexOf", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.get(1).is(STRING)) {
				checkString("indexOf", args.get(0));
				out.push(RödaInteger.of(args.get(1).str().indexOf(args.get(0).str())));
			}
			else {
				checkList("indexOf", args.get(1));
				for (int i = 0; i < args.get(1).list().size(); i++) {
					if (args.get(1).list().get(i).strongEq(args.get(0))) {
						out.push(RödaInteger.of(i));
						return;
					}
				}
				out.push(RödaInteger.of(-1));
			}
		}, Arrays.asList(new Parameter("sequence", false), new Parameter("str", false)), true));
	}
}
