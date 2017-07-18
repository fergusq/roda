package org.kaivos.röda.commands;

import static org.kaivos.röda.RödaValue.FLOATING;
import static org.kaivos.röda.RödaValue.INTEGER;
import static org.kaivos.röda.RödaValue.NUMBER;

import java.util.Arrays;
import java.util.function.Function;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaFloating;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public final class MathPopulator {
	
	private MathPopulator() {}
	
	private static void addMathFunction(RödaScope S, String name, Function<Double, Double> f) {
		S.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			out.push(RödaFloating.of(f.apply(args.get(0).floating())));
		}, Arrays.asList(new Parameter("arg", false, NUMBER)), false));
	}

	public static void populateMath(RödaScope S) {
		S.setLocal("E", RödaFloating.of(Math.E));
		S.setLocal("PI", RödaFloating.of(Math.PI));

		addMathFunction(S, "floor", Math::floor);
		addMathFunction(S, "ceil", Math::ceil);
		S.setLocal("round", RödaNativeFunction.of("round", (typeargs, args, kwargs, scope, in, out) -> {
			out.push(RödaInteger.of(Math.round(args.get(0).floating())));
		}, Arrays.asList(new Parameter("arg", false, NUMBER)), false));
		
		S.setLocal("abs", RödaNativeFunction.of("abs", (typeargs, args, kwargs, scope, in, out) -> {
			RödaValue arg = args.get(0);
			if (arg.is(INTEGER)) out.push(RödaInteger.of(Math.abs(arg.integer())));
			else if (arg.is(FLOATING)) out.push(RödaFloating.of(Math.abs(arg.floating())));
		}, Arrays.asList(new Parameter("variables", false, NUMBER)), false));
		
		addMathFunction(S, "signum", Math::signum);

		addMathFunction(S, "sin", Math::sin);
		addMathFunction(S, "cos", Math::cos);
		addMathFunction(S, "tan", Math::tan);
		addMathFunction(S, "asin", Math::asin);
		addMathFunction(S, "acos", Math::acos);
		addMathFunction(S, "atan", Math::atan);
		S.setLocal("atan2", RödaNativeFunction.of("atan2", (typeargs, args, kwargs, scope, in, out) -> {
			out.push(RödaFloating.of(Math.atan2(args.get(0).floating(), args.get(1).floating())));
		}, Arrays.asList(new Parameter("x", false, NUMBER), new Parameter("y", false, NUMBER)), false));
		addMathFunction(S, "sinh", Math::sinh);
		addMathFunction(S, "cosh", Math::cosh);
		addMathFunction(S, "tanh", Math::tanh);
		
		addMathFunction(S, "sqrt", Math::sqrt);
		addMathFunction(S, "cbrt", Math::cbrt);

		addMathFunction(S, "exp", Math::exp);
		addMathFunction(S, "ln", Math::log);
		addMathFunction(S, "log10", Math::log10);
		S.setLocal("log", RödaNativeFunction.of("log", (typeargs, args, kwargs, scope, in, out) -> {
			out.push(RödaFloating.of(Math.log(args.get(1).floating()) / Math.log(args.get(0).floating())));
		}, Arrays.asList(new Parameter("base", false, NUMBER), new Parameter("arg", false, NUMBER)), false));
	}
}
