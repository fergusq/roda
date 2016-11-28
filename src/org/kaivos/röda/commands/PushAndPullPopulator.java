package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.argumentOverflow;
import static org.kaivos.röda.Interpreter.checkReference;
import static org.kaivos.röda.Interpreter.error;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaNativeFunction;

public final class PushAndPullPopulator {
	
	private PushAndPullPopulator() {}

	public static void populatePushAndPull(RödaScope S) {
		S.setLocal("push", RödaNativeFunction.of("push", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.isEmpty())
				argumentUnderflow("push", 1, 0);
			for (RödaValue value : args) {
				out.push(value);
			}
		}, Arrays.asList(new Parameter("values", false)), true));
	
		S.setLocal("pull", RödaNativeFunction.of("pull", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.isEmpty()) {
				RödaValue value = in.pull();
				if (value == null) error("empty stream");
				out.push(value);
				return;
			}
			
			for (RödaValue value : args) {
				checkReference("pull", value);

				RödaValue pulled = in.pull();
				if (pulled == null) error("empty stream");
				value.assignLocal(pulled);
			}
		}, Arrays.asList(new Parameter("variables", true)), true));
	
		S.setLocal("peek", RödaNativeFunction.of("peek", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.isEmpty()) {
				RödaValue value = in.peek();
				if (value == null) error("empty stream");
				out.push(value);
				return;
			}
			
			if (args.size() > 1)
				argumentOverflow("peek", 1, 0);
			
			RödaValue value = args.get(0);
			checkReference("peek", value);
			
			RödaValue value2 = in.peek();
			if (value2 == null) error("empty stream");
			value.assignLocal(value2);
		}, Arrays.asList(new Parameter("variable", true)), true));
	}
}
