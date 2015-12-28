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
		.addOperatorRule("!=")
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
	
	private static OperatorLibrary<Integer> library;
	private static OperatorPrecedenceParser<Integer> opparser;
	private static TokenList tokenlist;
        
	static {
		/* Declares the operator library – all operators use parsePrimary() as their RHS parser */
		library = new OperatorLibrary<>(tl -> parsePrimary(tl));
		
		/* Declares the operators*/
		library.add("&&", (a, b) -> a != 0 && b != 0 ? 1 : 0);
		library.add("||", (a, b) -> a != 0 || b != 0 ? 1 : 0);
		library.add("^^", (a, b) -> a != 0 ^  b != 0 ? 1 : 0);
		library.increaseLevel();
		library.add("=", (a, b) -> a == b ? 1 : 0);
		library.add("!=", (a, b) -> a != b ? 1 : 0);
		library.increaseLevel();
		library.add("<", (a, b) -> a < b ? 1 : 0);
		library.add(">", (a, b) -> a > b ? 1 : 0);
		library.add("<=", (a, b) -> a <= b ? 1 : 0);
		library.add(">=", (a, b) -> a >= b ? 1 : 0);
		library.increaseLevel();
		library.add("&", (a, b) -> a & b);
		library.add("|", (a, b) -> a | b);
		library.add("^", (a, b) -> a ^ b);
		library.add("<<", (a, b) -> a << b);
		library.add(">>", (a, b) -> a >> b);
		library.add(">>>", (a, b) -> a >>> b);
		library.increaseLevel();
		library.add("+", (a, b) -> a + b);
		library.add("-", (a, b) -> a - b);
		library.increaseLevel();
		library.add("*", (a, b) -> a * b);
		library.add("/", (a, b) -> a / b);
		library.increaseLevel();
		library.add("**", (a, b) -> (int) Math.pow(a, b));
		
		/* Declares the OPP*/
		opparser = OperatorPrecedenceParser.fromLibrary(library);
	}
	
	private static int parse(TokenList tl) {
		return parseExpression(tl);
	}
	
	private static int parseExpression(TokenList tl) {
		return opparser.parse(tl);
	}
	
	private static int parsePrimary(TokenList tl) {
		if (tl.isNext("-")) {
			tl.accept("-");
			return -parsePrimary(tl);
		}
		if (tl.isNext("abs", "exp", "ln", "sin", "cos", "tan", "sqrt", "cbrt")) {
			String function = tl.nextString();
			tl.accept("(");
			int i = (int) evalFunction(function, parseExpression(tl));
			tl.accept(")");
			return i;
		}
		if (tl.isNext("(")) {
			tl.accept("(");
			int i = parseExpression(tl);
			tl.accept(")");
			return i;
		}
		if (tl.isNext("E")) {
			tl.accept("E");
			return (int) Math.E;
		}
		if (tl.isNext("PI")) {
			tl.accept("PI");
			return (int) Math.PI;
		}
		Token t = tl.seek();
		try {
			return Integer.parseInt(tl.nextString());
		} catch (NumberFormatException e) {
			throw new ParsingException(TokenList.expected(
								      "-", "(",
								      "abs",
								      "exp", "ln",
								      "sin", "cos", "tan",
								      "sqrt", "cbrt",
								      "E", "PI"), t);
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

	public static int eval(String expression) {
		return parse(t.tokenize(expression, "<string>"));
	}
}
