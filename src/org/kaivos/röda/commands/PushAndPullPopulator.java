package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkReference;
import static org.kaivos.röda.Interpreter.emptyStream;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaStream;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaBoolean;
import org.kaivos.röda.type.RödaNativeFunction;

public final class PushAndPullPopulator {
	
	private PushAndPullPopulator() {}
	
	private static void addPushingFunction(RödaScope S, String name, boolean isIn, BiConsumer<RödaStream, RödaValue> body) {
		S.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			if (args.isEmpty())
				argumentUnderflow(name, 1, 0);
			for (RödaValue value : args) {
				body.accept(isIn ? in : out, value);
			}
		}, Arrays.asList(new Parameter("values", false)), true));
	}

	private static void addPullingFunction(RödaScope S, String name,
			boolean returnSuccess, Function<RödaStream, RödaValue> body) {
		S.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			if (args.isEmpty()) {
				if (returnSuccess) argumentUnderflow(name, 1, 0);
				RödaValue value = body.apply(in);
				if (value == null) emptyStream("empty stream");
				out.push(value);
				return;
			}
			
			for (RödaValue value : args) {
				checkReference(name, value);

				RödaValue pulled = body.apply(in);
				if (returnSuccess) {
					out.push(RödaBoolean.of(pulled != null));
				}
				else if (pulled == null) emptyStream("empty stream");
				value.assignLocal(pulled);
			}
		}, Arrays.asList(new Parameter("variables", true)), true));
	}
	
	public static void populatePushAndPull(RödaScope S) {
		addPushingFunction(S, "push", false, RödaStream::push);
		addPushingFunction(S, "unpull", true, RödaStream::unpull);
		
		addPullingFunction(S, "pull", false, RödaStream::pull);
		addPullingFunction(S, "tryPull", true, RödaStream::pull);
	
		addPullingFunction(S, "peek", false, RödaStream::peek);
		addPullingFunction(S, "tryPeek", true, RödaStream::peek);
		
		S.setLocal("open", RödaNativeFunction.of("open", (typeargs, args, kwargs, scope, in, out) -> {
			out.push(RödaBoolean.of(in.open()));
		}, Arrays.asList(new Parameter("values", false)), true));
	}
}
