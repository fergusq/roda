package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkList;
import static org.kaivos.röda.Interpreter.illegalArguments;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;

public final class ShiftPopulator {

	private ShiftPopulator() {}
	
	public static final void populateShift(RödaScope S) {
		S.setLocal("shift", RödaNativeFunction.of("shift", (typeargs, args, kwargs, scope, in, out) -> {
			for (RödaValue list : args) {
				checkList("shift", list);
				if (list.list().isEmpty()) illegalArguments("illegal use of shift: all lists must be non-empty");
				list.modifiableList().remove(0);
			}
		}, Arrays.asList(new Parameter("first_list", false), new Parameter("other_lists", false)), true));
	}
}
