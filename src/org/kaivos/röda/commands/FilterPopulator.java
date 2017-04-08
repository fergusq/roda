package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.typeMismatch;
import static org.kaivos.röda.RödaValue.BOOLEAN;
import static org.kaivos.röda.RödaValue.FUNCTION;
import static org.kaivos.röda.RödaValue.STRING;

import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;

import org.kaivos.röda.Interpreter;
import org.kaivos.röda.RödaStream;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;

public final class FilterPopulator {

	private FilterPopulator() {}
	
	private static boolean evalCond(Interpreter I, RödaValue cond, RödaValue val) {
		RödaStream in = RödaStream.makeEmptyStream();
		RödaStream out = RödaStream.makeStream();
		I.exec("<filter populator>", 0,
				cond,
				Collections.emptyList(), Arrays.asList(val), Collections.emptyMap(),
				new RödaScope(I.G), in, out);
		out.finish();
		boolean retval = true;
		while (true) {
			RödaValue cval = out.pull();
			if (cval == null) break;
			if (!cval.is(BOOLEAN))
				typeMismatch("condition returned a value of type '" + cval.typeString()
					+ "', expected boolean");
			retval &= cval.bool();
		}
		return retval;
	}

	public static void populateFilterAndGrep(Interpreter I, RödaScope S) {
		S.setLocal("filter", RödaNativeFunction.of("filter", (typeargs, args, kwargs, scope, in, out) -> {
			in.forAll(val -> {
				if (evalCond(I, args.get(0), val)) out.push(val);
			});
		}, Arrays.asList(new Parameter("cond", false, FUNCTION)), false));
		
		S.setLocal("grep", RödaNativeFunction.of("grep", (typeargs, args, kwargs, scope, in, out) -> {
			Pattern[] patterns = new Pattern[args.size()];
			for (int i = 0; i < patterns.length; i++) patterns[i] = Pattern.compile(args.get(i).str());
			in.forAll(val -> {
				for (Pattern p : patterns) {
					if (p.matcher(val.str()).matches()) {
						out.push(val);
					}
				}
			});
		}, Arrays.asList(new Parameter("patterns", false, STRING)), true));
	}
}
