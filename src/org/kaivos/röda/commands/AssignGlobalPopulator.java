package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkString;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;

public final class AssignGlobalPopulator {
	
	private AssignGlobalPopulator() {}

	public static void populateAssignGlobal(RödaScope S) {
		S.setLocal("assignGlobal", RödaNativeFunction.of("assignGlobal", (typeargs, args, kwargs, scope, in, out) -> {
			checkString("assignGlobal", args.get(0));
			String variableName = args.get(0).str();
			S.setLocal(variableName, args.get(1));
	    }, Arrays.asList(new Parameter("variable", false), new Parameter("value", false)), true));
	
	    S.setLocal("createGlobal", RödaNativeFunction.of("createGlobal", (typeargs, args, kwargs, scope, in, out) -> {
			checkString("createGlobal", args.get(0));
	        String variableName = args.get(0).str();
			if (S.resolve(variableName) == null)
				S.setLocal(variableName, args.get(1));
	    }, Arrays.asList(new Parameter("variable", false), new Parameter("value", false)), true));
	}
}
