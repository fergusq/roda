package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkReference;
import static org.kaivos.röda.Interpreter.error;

import java.util.Arrays;
import java.util.Random;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaBoolean;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class RandomPopulator {

	private RandomPopulator() {}

	public static void populateRandom(RödaScope S) {
		Random rnd = new Random();
		
		S.setLocal("random", RödaNativeFunction.of("random", (typeargs, args, scope, in, out) -> {
					final int INTEGER=0,
						FLOAT=1,
						BOOLEAN=2;
					java.util.function.Function<Integer, RödaValue> next = i -> {
						switch (i) {
						case INTEGER: return RödaInteger.of(rnd.nextInt());
						case FLOAT: return RödaString.of(rnd.nextDouble()+"");
						case BOOLEAN: return RödaBoolean.of(rnd.nextBoolean());
						}
						return null;
					};
					int mode = BOOLEAN;
					if (args.size() == 1 && args.get(0).is(RödaValue.FLAG)) {
						switch (args.get(0).str()) {
						case "-integer": mode = INTEGER; break;
						case "-boolean": mode = BOOLEAN; break;
						case "-float": mode = FLOAT; break;
						default: error("random: invalid flag " + args.get(0).str());
						}
						args.remove(0);
					}
					if (args.size() == 0) {
						out.push(next.apply(mode));
						return;
					}
					for (RödaValue variable : args) {
						checkReference("random", variable);
						variable.assign(next.apply(mode));
					}
				}, Arrays.asList(new Parameter("flags_and_variables", true)), true));
	}
}
