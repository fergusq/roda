package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.emptyStream;
import static org.kaivos.röda.Interpreter.illegalArguments;
import static org.kaivos.röda.Interpreter.outOfBounds;
import static org.kaivos.röda.RödaValue.INTEGER;
import static org.kaivos.röda.RödaValue.LIST;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;

public final class InterleavePopulator {

	private InterleavePopulator() {}
	
	public static void populateInterleave(RödaScope S) {
		S.setLocal("interleave", RödaNativeFunction.of("interleave", (typeargs, args, kwargs, scope, in, out) -> {
			int length = args.get(0).list().size();
			
			for (RödaValue list : args) {
				if (list.list().size() != length)
					illegalArguments("illegal use of interleave: all lists must have the same size");
			}
			
			for (int i = 0; i < length; i++) {
				for (RödaValue list : args) {
					out.push(list.list().get(i));
				}
			}
		}, Arrays.asList(new Parameter("first_list", false, LIST), new Parameter("other_lists", false, LIST)), true));
		S.setLocal("slide", RödaNativeFunction.of("slide", (typeargs, args, kwargs, scope, in, out) -> {
			long n = args.get(0).integer();
			if (n > Integer.MAX_VALUE || n < 1) outOfBounds("invalid subsequence length: " + n
					+ " (valid range: 1 <= n <= "  + Integer.MAX_VALUE + ")");
			RödaValue[] array = new RödaValue[(int) n];
			for (int i = 0; i < n; i++) {
				RödaValue val = in.pull();
				if (val == null) emptyStream("empty stream");
				array[i] = val;
				out.push(val);
			}
			int i = 0;
			RödaValue val;
			while ((val = in.pull()) != null) {
				array[(int)(i++%n)] = val;
				for (int j = 0; j < n; j++) {
					out.push(array[(int)((i+j)%n)]);
				}
			}
		}, Arrays.asList(new Parameter("n", false, INTEGER)), false));
	}
}
