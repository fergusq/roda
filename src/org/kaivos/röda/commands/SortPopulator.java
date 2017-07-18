package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentOverflow;
import static org.kaivos.röda.Interpreter.emptyStream;
import static org.kaivos.röda.Interpreter.fullStream;
import static org.kaivos.röda.Interpreter.illegalArguments;
import static org.kaivos.röda.Interpreter.outOfBounds;
import static org.kaivos.röda.Interpreter.typeMismatch;
import static org.kaivos.röda.RödaValue.BOOLEAN;
import static org.kaivos.röda.RödaValue.INTEGER;
import static org.kaivos.röda.RödaValue.LIST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.ExpressionTree.CType;
import org.kaivos.röda.RödaStream;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;

public class SortPopulator {

	private SortPopulator() {}

	private static RödaValue evalKey(Interpreter I, RödaValue key, RödaValue arg) {
		RödaStream in = RödaStream.makeEmptyStream();
		RödaStream out = RödaStream.makeStream();
		I.exec("<sort populator>", 0,
				key,
				Collections.emptyList(), Arrays.asList(arg), Collections.emptyMap(),
				new RödaScope(I.G), in, out);
		out.finish();
		RödaValue retval = null;
		while (true) {
			if (retval != null) fullStream("stream is full (in <sort populator>");
			retval = out.pull();
			if (retval == null) emptyStream("empty stream (in <sort populator>)");
			return retval;
		}
	}

	private static int evalCmp(Interpreter I, RödaValue cmp, RödaValue a, RödaValue b) {
		RödaStream in = RödaStream.makeEmptyStream();
		RödaStream out = RödaStream.makeStream();
		I.exec("<sort populator>", 0,
				cmp,
				Collections.emptyList(), Arrays.asList(a, b), Collections.emptyMap(),
				new RödaScope(I.G), in, out);
		out.finish();
		boolean useBool = false, retval = true;
		while (true) {
			RödaValue val = out.pull();
			if (val == null) break;
			if (!useBool && val.is(INTEGER)) {
				long l = val.integer();
				if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE)
					outOfBounds("comparator returned an illegal integer: " + l + " (not in supported range "
							+ Integer.MIN_VALUE + ".." + Integer.MAX_VALUE + ")");
				return (int) l;
			}
			else useBool = true;
			if (!val.is(BOOLEAN))
				typeMismatch("comparator returned a value of type '" + val.typeString()
					+ "', it should only return a single integer or an arbitrary number of boolean values");
			retval &= val.bool();
		}
		return retval ? -1 : 1;
	}
	
	public static void populateSort(Interpreter I, RödaScope S) {
		S.setLocal("sort", RödaNativeFunction.of("sort", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() > 1)
				argumentOverflow("head", 1, args.size());
			List<RödaValue> list;
			if (args.size() == 0) {
				list = new ArrayList<>();
				in.forAll(list::add);
			} else {
				list = new ArrayList<>(args.get(0).list());
			}
			if (kwargs.containsKey("key") && kwargs.containsKey("cmp")) {
				illegalArguments("received both 'key' and 'cmp', only one should be provided");
			}
			if (kwargs.containsKey("key")) {
				RödaValue key = kwargs.get("key");
				list.sort((a, b) -> {
					a = evalKey(I, key, a);
					b = evalKey(I, key, b);
					return a.callOperator(CType.LT, b).bool() ? -1 : a.strongEq(b) ? 0 : 1;
				});
			}
			else if (kwargs.containsKey("cmp")) {
				RödaValue cmp = kwargs.get("cmp");
				list.sort((a, b) -> {
					return evalCmp(I, cmp, a, b);
				});
			}
			else {
				list.sort((a, b) -> a.callOperator(CType.LT, b).bool() ? -1 : a.strongEq(b) ? 0 : 1);
			}
			list.forEach(out::push);
		}, Arrays.asList(new Parameter("number", false, LIST)), true,
				Collections.emptyList(), true));
	}
}
