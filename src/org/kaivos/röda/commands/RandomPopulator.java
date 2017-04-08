package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkReference;

import java.util.Arrays;
import java.util.Random;
import java.util.function.Supplier;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaBoolean;
import org.kaivos.röda.type.RödaFloating;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class RandomPopulator {

	private static Random rnd = new Random();
	
	private RandomPopulator() {}
	
	private static void addQueryType(RödaScope S, String name, Supplier<RödaValue> supplier) {
		S.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() == 0) {
				out.push(supplier.get());
				return;
			}
			for (RödaValue variable : args) {
				checkReference(name, variable);
				variable.assignLocal(supplier.get());
			}
		}, Arrays.asList(new Parameter("variables", true)), true));
	}

	public static void populateRandom(RödaScope S) {
		
		S.setLocal("randomize", RödaNativeFunction.of("randomize", (typeargs, args, kwargs, scope, in, out) -> {
			RödaValue seed = args.get(0);
			if (seed.is(RödaValue.INTEGER)) {
				synchronized (rnd) {
					rnd = new Random(seed.integer());
				}
			}
			else {
				synchronized (rnd) {
					rnd = new Random(seed.str().hashCode());
				}
			}
		}, Arrays.asList(new Parameter("seed", false)), false));
		
		addQueryType(S, "randomInteger", () -> RödaInteger.of(rnd.nextInt()));
		addQueryType(S, "randomFloating", () -> RödaFloating.of(rnd.nextDouble()));
		addQueryType(S, "randomBoolean", () -> RödaBoolean.of(rnd.nextBoolean()));
	}
}
