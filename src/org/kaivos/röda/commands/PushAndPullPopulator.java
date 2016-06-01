package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkReference;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaNativeFunction;

public final class PushAndPullPopulator {
	
	private PushAndPullPopulator() {}

	public static void populatePushAndPull(RödaScope S) {
		S.setLocal("push", RödaNativeFunction.of("push", (typeargs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("push", 1, 0);
						return;
					}
					for (RödaValue value : args) {
						out.push(value);
					}
				}, Arrays.asList(new Parameter("values", false)), true));
	
		S.setLocal("pull", RödaNativeFunction.of("pull", (typeargs, args, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("pull", 1, 0);
						return;
					}
					for (RödaValue value : args) {
						checkReference("pull", value);
					    
					    RödaValue pulled = in.pull();
						if (pulled == null) {
							continue;
						}
						value.assign(pulled);
					}
				}, Arrays.asList(new Parameter("variables", true)), true));
	}
}
