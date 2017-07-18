package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.illegalArguments;
import static org.kaivos.röda.RödaValue.MAP;
import static org.kaivos.röda.RödaValue.NAMESPACE;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public class KeysPopulator {
	
	private KeysPopulator() {}
	
	public static void populateKeys(RödaScope S) {
		S.setLocal("keys", RödaNativeFunction.of("keys", (typeargs, args, kwargs, scope, in, out) -> {
			RödaValue arg = args.get(0);
			if (!arg.is(MAP) && !arg.is(NAMESPACE))
				illegalArguments("illegal argument 'table' for 'keys': "
						+ "map or namespace expected (got " + arg.typeString() + ")");
			(arg.is(MAP) ? arg.map().keySet() : arg.scope().getLocalVariableNames())
				.stream().map(RödaString::of).forEach(out::push);
		}, Arrays.asList(new Parameter("table", false)), false));
	}

}
