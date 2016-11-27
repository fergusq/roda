package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentOverflow;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.RödaValue.INTEGER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaNativeFunction;

public final class HeadAndTailPopulator {

	private HeadAndTailPopulator() {
	}

	public static void populateHeadAndTail(RödaScope S) {
		S.setLocal("head", RödaNativeFunction.of("head", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() > 1)
				argumentOverflow("head", 1, args.size());
			if (args.size() == 0) {
				RödaValue input = in.pull();
				if (input == null)
					error("head: input stream is closed");
				out.push(input);
			} else {
				long num = args.get(0).integer();
				for (int i = 0; i < num; i++) {
					RödaValue input = in.pull();
					if (input == null)
						error("head: input stream is closed");
					out.push(input);
				}
			}
		}, Arrays.asList(new Parameter("number", false, INTEGER)), true));

		S.setLocal("tail", RödaNativeFunction.of("tail", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() > 1)
				argumentOverflow("tail", 1, args.size());

			long numl;

			if (args.size() == 0)
				numl = 1;
			else {
				numl = args.get(0).integer();
				if (numl > Integer.MAX_VALUE)
					error("tail: too large number: " + numl);
			}

			int num = (int) numl;

			List<RödaValue> values = new ArrayList<>();
			for (RödaValue value : in) {
				values.add(value);
			}
			if (values.size() < num)
				error("tail: input stream is closed");

			for (int i = values.size() - num; i < values.size(); i++) {
				out.push(values.get(i));
			}

		}, Arrays.asList(new Parameter("number", false, INTEGER)), true));
	}
}
