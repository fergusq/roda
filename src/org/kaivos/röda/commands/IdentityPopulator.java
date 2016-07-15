package org.kaivos.röda.commands;

import java.util.Collections;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.type.RödaNativeFunction;

public final class IdentityPopulator {
	
	private IdentityPopulator() {}

	public static void populateIdentity(RödaScope S) {
		S.setLocal("identity", RödaNativeFunction.of("identity", (typeargs, args, scope, in, out) -> {
			in.forAll(out::push);
		}, Collections.emptyList(), false));
	}
}
