package org.kaivos.röda.commands;

import static org.kaivos.röda.RödaValue.MAP;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public class KeysPopulator {
	
	private KeysPopulator() {}
	
	public static void populateKeys(RödaScope S) {
		S.setLocal("keys", RödaNativeFunction.of("keys", (typeargs, args, kwargs, scope, in, out) -> {
			args.get(0).map().keySet().stream().map(RödaString::of).forEach(out::push);
		}, Arrays.asList(new Parameter("table", false, MAP)), false));
	}

}
