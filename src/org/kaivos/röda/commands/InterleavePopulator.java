package org.kaivos.röda.commands;

import java.util.Arrays;

import static org.kaivos.röda.Interpreter.illegalArguments;
import static org.kaivos.röda.RödaValue.LIST;

import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.type.RödaNativeFunction;

public final class InterleavePopulator {

	private InterleavePopulator() {}
	
	public static void populateInterleave(RödaScope S) {
		S.setLocal("interleave", RödaNativeFunction.of("interleave", (typeargs, args, kwargs, scope, in, out) -> {
			int length = args.get(0).list().size();
			
			for (RödaValue list : args) {
				if (list.list().size() != length)
					illegalArguments("illegal use of interleave: all lists must have the same size");
			}
			
			for (int i = 0; i < length; i++) {
				for (RödaValue list : args) {
					out.push(list.list().get(i));
				}
			}
		}, Arrays.asList(new Parameter("first_list", false, LIST), new Parameter("other_lists", false, LIST)), true));
	}
}
