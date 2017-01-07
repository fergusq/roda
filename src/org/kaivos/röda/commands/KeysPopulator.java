package org.kaivos.röda.commands;

import static java.util.stream.Collectors.toList;
import static org.kaivos.röda.RödaValue.MAP;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.type.RödaList;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public class KeysPopulator {
	
	private KeysPopulator() {}
	
	public static void populateKeys(RödaScope S) {
		S.setLocal("keys", RödaNativeFunction.of("keys", (typeargs, args, kwargs, scope, in, out) -> {
			out.push(RödaList.of(args.get(0).map().keySet().parallelStream().map(RödaString::of).collect(toList())));
		}, Arrays.asList(new Parameter("table", false, MAP)), false));
	}

}
