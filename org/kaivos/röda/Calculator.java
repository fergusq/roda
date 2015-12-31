package org.kaivos.röda;

import org.kaivos.nept.parser.OperatorPrecedenceParser;
import org.kaivos.nept.parser.OperatorLibrary;
import org.kaivos.nept.parser.TokenList;
import org.kaivos.nept.parser.TokenScanner;
import org.kaivos.nept.parser.Token;
import org.kaivos.nept.parser.ParsingException;

public class Calculator {
	private static TokenScanner t = new TokenScanner()
		.addOperators("+-*/%^&|~=<>()E")
		.addOperatorRule("&&")
		.addOperatorRule("||")
		.addOperatorRule("^^")
		.addOperatorRule("!=")
		.addOperatorRule("<=")
		.addOperatorRule(">=")
		.addOperatorRule("abs")
		.addOperatorRule("exp")
		.addOperatorRule("ln")
		.addOperatorRule("sin")
		.addOperatorRule("cos")
		.addOperatorRule("tan")
		.addOperatorRule("sqrt")
		.addOperatorRule("cbrt")
		.addOperatorRule("PI")
		.separateIdentifiersAndPunctuation(false)
		.addCommentRule("/*", "*/")
		.appendOnEOF("<EOF>");
	
	private static OperatorLibrary<Double> library;
	private static OperatorPrecedenceParser<Double> opparser;
        
	static {
		/* Declares the operator library – all operators use parsePrimary() as their RHS parser */
		library = new OperatorLibrary<>(tl -> parsePrimary(tl));
		
		/* Declares the operators*/
		library.add("&&", (a, b) -> a != 0 && b != 0 ? 1d : 0d);
		library.add("||", (a, b) -> a != 0 || b != 0 ? 1d : 0d);
		library.add("^^", (a, b) -> a != 0 ^  b != 0 ? 1d : 0d);
		library.increaseLevel();
		library.add("=", (a, b) -> a == b ? 1d : 0d);
		library.add("!=", (a, b) -> a != b ? 1d : 0d);
		library.increaseLevel();
		library.add("<", (a, b) -> a < b ? 1d : 0d);
		library.add(">", (a, b) -> a > b ? 1d : 0d);
		library.add("<=", (a, b) -> a <= b ? 1d : 0d);
		library.add(">=", (a, b) -> a >= b ? 1d : 0d);
		library.increaseLevel();
		library.add("&", (a, b) -> (double) (a.intValue() & b.intValue()));
		library.add("|", (a, b) -> (double) (a.intValue() | b.intValue()));
		library.add("^", (a, b) -> (double) (a.intValue() ^ b.intValue()));
		library.add("<<", (a, b) -> (double) (a.intValue() << b.intValue()));
		library.add(">>", (a, b) -> (double) (a.intValue() >> b.intValue()));
		library.add(">>>", (a, b) -> (double) (a.intValue() >>> b.intValue()));
		library.increaseLevel();
		library.add("+", (a, b) -> a + b);
		library.add("-", (a, b) -> a - b);
		library.increaseLevel();
		library.add("*", (a, b) -> a * b);
		library.add("/", (a, b) -> a / b);
		library.increaseLevel();
		library.add("**", (a, b) -> Math.pow(a, b));
		
		/* Declares the OPP*/
		opparser = OperatorPrecedenceParser.fromLibrary(library);
	}
	
	private static double parse(TokenList tl) {
		return parseExpression(tl);
	}
	
	private static double parseExpression(TokenList tl) {
		return opparser.parse(tl);
	}
	
	private static double parsePrimary(TokenList tl) {
		if (tl.isNext("-")) {
			tl.accept("-");
			return -parsePrimary(tl);
		}
		if (tl.isNext("abs", "exp", "ln", "sin", "cos", "tan", "sqrt", "cbrt")) {
			String function = tl.nextString();
			tl.accept("(");
			double i = evalFunction(function, parseExpression(tl));
			tl.accept(")");
			return i;
		}
		if (tl.isNext("(")) {
			tl.accept("(");
			double i = parseExpression(tl);
			tl.accept(")");
			return i;
		}
		if (tl.isNext("E")) {
			tl.accept("E");
			return Math.E;
		}
		if (tl.isNext("PI")) {
			tl.accept("PI");
			return Math.PI;
		}
		Token t = tl.seek();
		try {
			return Double.parseDouble(tl.nextString());
		} catch (NumberFormatException e) {
			throw new ParsingException(TokenList.expected(
								      "-", "(",
								      "abs",
								      "exp", "ln",
								      "sin", "cos", "tan",
								      "sqrt", "cbrt",
								      "E", "PI", "<number>"), t);
		}
	}

	private static double evalFunction(String name, double x) {
		switch (name) {
		case "abs": return Math.abs(x);
		case "exp": return Math.exp(x);
		case "ln":  return Math.log(x);
		case "sin": return Math.sin(x);
		case "cos": return Math.cos(x);
		case "tan": return Math.tan(x);
		case "sqrt": return Math.sqrt(x);
		case "cbrt": return Math.sqrt(x);
		default: break;
		}
		assert false;
		return -1;
	}

	public static double eval(String expression) {
		return parse(t.tokenize(expression, "<string>"));
	}
}
