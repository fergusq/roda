package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkReference;
import static org.kaivos.röda.Interpreter.error;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaStream;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaBoolean;
import org.kaivos.röda.type.RödaNativeFunction;

public final class PushAndPullPopulator {
	
	private PushAndPullPopulator() {}

	private static void addPullingFunction(RödaScope S, String name,
			boolean returnSuccess, Function<RödaStream, RödaValue> body) {
		S.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			if (args.isEmpty()) {
				if (returnSuccess) argumentUnderflow(name, 1, 0);
				RödaValue value = body.apply(in);
				if (value == null) error("empty stream");
				out.push(value);
				return;
			}
			
			for (RödaValue value : args) {
				checkReference(name, value);

				RödaValue pulled = body.apply(in);
				if (returnSuccess) {
					out.push(RödaBoolean.of(pulled != null));
				}
				else if (pulled == null) error("empty stream");
				value.assignLocal(pulled);
			}
		}, Arrays.asList(new Parameter("variables", true)), true, Collections.emptyList(), true));
	}
	
	public static void populatePushAndPull(RödaScope S) {
		S.setLocal("push", RödaNativeFunction.of("push", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.isEmpty())
				argumentUnderflow("push", 1, 0);
			for (RödaValue value : args) {
				out.push(value);
			}
		}, Arrays.asList(new Parameter("values", false)), true, Collections.emptyList(), true));
	
		addPullingFunction(S, "pull", false, RödaStream::pull);
		addPullingFunction(S, "tryPull", true, RödaStream::pull);
	
		addPullingFunction(S, "peek", false, RödaStream::peek);
		addPullingFunction(S, "tryPeek", true, RödaStream::peek);
	}
}
