package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkList;
import static org.kaivos.röda.Interpreter.error;

import java.util.Arrays;

import org.kaivos.röda.RödaValue;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;

public final class ShiftPopulator {

	private ShiftPopulator() {}
	
	public static final void populateShift(RödaScope S) {
		S.setLocal("shift", RödaNativeFunction.of("shift", (typeargs, args, kwargs, scope, in, out) -> {
			for (RödaValue listref : args) {
				RödaValue list = listref.resolve(false);
				checkList("shift", list);
				if (list.list().isEmpty()) error("illegal use of shift: all lists must be non-empty");
				list.modifiableList().remove(0);
			}
		}, Arrays.asList(new Parameter("first_list", true), new Parameter("other_lists", true)), true));
	}
}
