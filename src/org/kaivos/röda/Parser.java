package org.kaivos.röda;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import java.util.regex.Pattern;

import java.util.function.BinaryOperator;

import org.kaivos.nept.parser.TokenList;
import org.kaivos.nept.parser.TokenScanner;
import org.kaivos.nept.parser.OperatorLibrary;
import org.kaivos.nept.parser.OperatorPrecedenceParser;
import org.kaivos.nept.parser.ParsingException;

import static org.kaivos.röda.RödaStream.StreamType;
import static org.kaivos.röda.RödaStream.ValueStream;
import static org.kaivos.röda.RödaStream.ByteStream;
import static org.kaivos.röda.RödaStream.LineStream;
import static org.kaivos.röda.RödaStream.VoidStream;

public class Parser {
	public static final TokenScanner t = new TokenScanner()
		.addOperatorRule("...")
		.addOperatorRule("..")
		.addOperatorRule("->")
		.addOperators("<>()[]{}|&.:;=#%\n")
		.separateIdentifiersAndPunctuation(false)
		.addCommentRule("/*", "*/")
		.addStringRule('\'', '\'', '\0')
		.addStringRule('"','"','\\')
		.addEscapeCode('\\', "\\")
		.addEscapeCode('n', "\n")
		.addEscapeCode('t', "\t")
		.addCharacterEscapeCode('x', 2, 16)
		.dontIgnore('\n')
		.appendOnEOF("<EOF>");

	private static void newline(TokenList tl) {
		if (tl.isNext("\n")) {
			tl.accept("\n");
		}
		else {
			tl.accept(";");
		}
		maybeNewline(tl);
	}

	private static void maybeNewline(TokenList tl) {
		while (tl.isNext("\n") || tl.isNext(";")) {
			tl.next();
		}
	}
	
	static class Program {
		List<Function> functions;
		Program(List<Function> functions) {
			this.functions = functions;
		}
	}
	
	static Program parse(TokenList tl) {
		//System.err.println(tl);

		maybeNewline(tl);

		List<Function> list = new ArrayList<>();
		while (!tl.isNext("<EOF>")) {
			list.add(parseFunction(tl));
			maybeNewline(tl);
		}
		return new Program(list);
	}

	static class Function {
		String name;
		List<Parameter> parameters;
		boolean isVarargs;
		StreamType input;
		StreamType output;
		List<Statement> body;

		Function(String name,
			 List<Parameter> parameters,
			 boolean isVarargs,
			 StreamType input,
			 StreamType output,
			 List<Statement> body) {

			this.name = name;
			this.parameters = parameters;
			this.isVarargs = isVarargs;
			this.input = input;
			this.output = output;
			this.body = body;
		}
	}

	static class Parameter {
		String name;
		boolean reference;
	        Parameter(String name, boolean reference) {
			this.name = name;
			this.reference = reference;
		}
	}

	static Parameter parseParameter(TokenList tl) {
		boolean reference = false;
		if (tl.isNext("&")) {
			tl.accept("&");
			reference = true;
		}
		String name = tl.nextString();
		return new Parameter(name, reference);
	}
	
	static Function parseFunction(TokenList tl) {
		String name = tl.nextString();
		List<Parameter> parameters = new ArrayList<>();
		boolean isVarargs = false;
		while (!tl.isNext(":") && !tl.isNext("{")) {
			parameters.add(parseParameter(tl));
			if (tl.isNext("...")) {
				tl.accept("...");
				isVarargs = true;
			}
		}
		maybeNewline(tl);
		StreamType input, output;
		if (tl.isNext(":")) {
			tl.accept(":");
			maybeNewline(tl);
			input = parseStreamType(tl);
			tl.accept("->");
			output = parseStreamType(tl);
			maybeNewline(tl);
		} else {
			input = new ValueStream();
			output = new ValueStream();
		}
		tl.accept("{");
		maybeNewline(tl);
		List<Statement> body = new ArrayList<>();
		while (!tl.isNext("}")) {
			body.add(parseStatement(tl));
			if (!tl.isNext("}"))
				newline(tl);
		}
		maybeNewline(tl);
		tl.accept("}");
		return new Function(name, parameters, isVarargs, input, output, body);
	}
	
	static StreamType parseStreamType(TokenList tl) {
		if (tl.isNext("void")) {
			tl.accept("void");
			return new VoidStream();
		}
		if (tl.isNext("byte")) {
			tl.accept("byte");
			return new ByteStream();
		}
		if (tl.isNext("line")) {
			tl.accept("line");
			if (tl.isNext("(")) {
				tl.accept(")");
				tl.accept("\"");
				String sep = tl.nextString();
				tl.accept("\"");
				return new LineStream(sep);
			}
			return new LineStream();
		}
		if (tl.isNext("value")) {
			tl.accept("value");
			return new ValueStream();
		}

		throw new ParsingException(TokenList.expected("byte", "line", "void", "value"), tl.next());
	}

	static class Statement {
		List<Command> commands;
		Statement(List<Command> commands) {
			this.commands = commands;
		}
	}

	static Statement parseStatement(TokenList tl) {
		List<Command> commands = new ArrayList<>();
		commands.add(parseCommand(tl));
		while (tl.isNext("|")) {
			tl.accept("|");
			maybeNewline(tl);
			commands.add(parseCommand(tl));
		}
		return new Statement(commands);
	}

	static class Command {
		enum Type {
			NORMAL,
			WHILE,
			IF,
			FOR,
			TRY,
			TRY_DO
		}
		Type type;
		Expression name;
		List<Expression> arguments;
		Statement cond;
		String variable;
		Expression list;
		List<Statement> body, elseBody;
		Command cmd;
		Command() {} // käytä apufunktioita alla
		String file;
		int line;
	}
	
	static Command _makeNormalCommand(String file, int line, Expression name, List<Expression> arguments) {
		Command cmd = new Command();
		cmd.type = Command.Type.NORMAL;
		cmd.file = file;
		cmd.line = line;
		cmd.name = name;
		cmd.arguments = arguments;
		return cmd;
	}

	static Command _makeIfOrWhileCommand(String file, int line, boolean isWhile,
					     Statement cond, List<Statement> body, List<Statement> elseBody) {
		Command cmd = new Command();
		cmd.type = isWhile ? Command.Type.WHILE : Command.Type.IF;
		cmd.file = file;
		cmd.line = line;
		cmd.cond = cond;
		cmd.body = body;
		cmd.elseBody = elseBody;
		return cmd;
	}
	
	static Command _makeForCommand(String file, int line, String variable, Expression list, List<Statement> body) {
		Command cmd = new Command();
		cmd.type = Command.Type.FOR;
		cmd.file = file;
		cmd.line = line;
		cmd.variable = variable;
		cmd.list = list;
		cmd.body = body;
		return cmd;
	}

	static Command _makeTryCommand(String file, int line, List<Statement> body) {
		Command cmd = new Command();
		cmd.type = Command.Type.TRY_DO;
		cmd.file = file;
		cmd.line = line;
		cmd.body = body;
		return cmd;
	}

	static Command _makeTryCommand(String file, int line, Command command) {
		Command cmd = new Command();
		cmd.type = Command.Type.TRY;
		cmd.file = file;
		cmd.line = line;
		cmd.cmd = command;
		return cmd;
	}
	
	static Command parseCommand(TokenList tl) {
		String file = tl.seek().getFile();
		int line = tl.seek().getLine();
		if (tl.isNext("while") || tl.isNext("if")) {
			boolean isWhile = tl.nextString().equals("while");
			Statement cond = parseStatement(tl);
			maybeNewline(tl);
			tl.accept("do");
			maybeNewline(tl);
			List<Statement> body = new ArrayList<>(), elseBody = null;
			while (!tl.isNext("done") && !tl.isNext("else")) {
				body.add(parseStatement(tl));
				newline(tl);
			}
			if (tl.isNext("else")) {
				tl.accept("else");
				maybeNewline(tl);
				elseBody = new ArrayList<>();
				while (!tl.isNext("done")) {
					elseBody.add(parseStatement(tl));
					newline(tl);
				}
			}
			tl.accept("done");
			return _makeIfOrWhileCommand(file, line, isWhile, cond, body, elseBody);
		}

		if (tl.isNext("for")) {
			tl.accept("for");
			String variable = tl.nextString();
			tl.accept("in");
			Expression list = parseExpression(tl);
			maybeNewline(tl);
			tl.accept("do");
			maybeNewline(tl);
			List<Statement> body = new ArrayList<>();
			while (!tl.isNext("done")) {
				body.add(parseStatement(tl));
				newline(tl);
			}
			tl.accept("done");
			return _makeForCommand(file, line, variable, list, body);
		}

		if (tl.isNext("try")) {
			tl.accept("try");
			maybeNewline(tl);
			if (tl.isNext("do")) {
				tl.accept("do");
				maybeNewline(tl);
				List<Statement> body = new ArrayList<>();
				while (!tl.isNext("done")) {
					body.add(parseStatement(tl));
					newline(tl);
				}
				tl.accept("done");
				return _makeTryCommand(file, line, body);
			} else {
				return _makeTryCommand(file, line, parseCommand(tl));
			}
		}

		Expression name = parseExpression(tl);
		List<Expression> arguments = new ArrayList<>();
		while (!tl.isNext("|") && !tl.isNext(";") && !tl.isNext("\n") && !tl.isNext(")") && !tl.isNext("}") && !tl.isNext("<EOF>")) {
			arguments.add(parseExpression(tl));
		}

		return _makeNormalCommand(file, line, name, arguments);
	}
	
	static class Expression {
		enum Type {
			VARIABLE,
			STRING,
			NUMBER,
			STATEMENT,
			BLOCK,
			LIST,
			LENGTH,
			ELEMENT,
			SLICE,
			CONCAT,
			JOIN,
			CALCULATOR
		}
		enum CType {
			MUL,
			DIV,
			ADD,
			SUB,
			BAND,
			BOR,
			BXOR,
			BRSHIFT,
			BRRSHIFT,
			BLSHIFT,
			EQ,
			NEQ,
			LT,
			GT,
			LE,
			GE,
			AND,
			OR,
			XOR,
			
			NEG,
			BNOT,
			NOT
		}
		Type type;
		CType ctype;
		boolean isUnary;
		String variable;
		String string;
		int number;
		Statement statement;
		Function block;
		List<Expression> list;
		Expression sub, index, index1, index2, exprA, exprB;

		String file;
		int line;
	}

	private static Expression expressionVariable(String file, int line, String t) {
		Expression e = new Expression();
		e.type = Expression.Type.VARIABLE;
		e.file = file;
		e.line = line;
		e.variable = t;
		return e;
	}

	private static Expression expressionString(String file, int line, String t) {
		Expression e = new Expression();
		e.type = Expression.Type.STRING;
		e.file = file;
		e.line = line;
		e.string = t;
		return e;
	}

	private static Expression expressionInt(String file, int line, int d) {
		Expression e = new Expression();
		e.type = Expression.Type.NUMBER;
		e.file = file;
		e.line = line;
		e.number = d;
		return e;
	}

	private static Expression expressionStatement(String file, int line, Statement statement) {
		Expression e = new Expression();
		e.type = Expression.Type.STATEMENT;
		e.file = file;
		e.line = line;
		e.statement = statement;
		return e;
	}

	private static Expression expressionFunction(String file, int line, Function block) {
		Expression e = new Expression();
		e.type = Expression.Type.BLOCK;
		e.file = file;
		e.line = line;
		e.block = block;
		return e;
	}

	private static Expression expressionList(String file, int line, List<Expression> list) { 
		Expression e = new Expression();
		e.type = Expression.Type.LIST;
		e.file = file;
		e.line = line;
		e.list = list;
		return e;
	}

	private static Expression expressionLength(String file, int line, Expression sub) {
		Expression e = new Expression();
		e.type = Expression.Type.LENGTH;
		e.file = file;
		e.line = line;
		e.sub = sub;
		return e;
	}

	private static Expression expressionElement(String file, int line, Expression list, Expression index) {
		Expression e = new Expression();
		e.type = Expression.Type.ELEMENT;
		e.file = file;
		e.line = line;
		e.sub = list;
		e.index = index;
		return e;
	}

	private static Expression expressionSlice(String file, int line, Expression list, Expression index1, Expression index2) {
		Expression e = new Expression();
		e.type = Expression.Type.SLICE;
		e.file = file;
		e.line = line;
		e.sub = list;
		e.index1 = index1;
		e.index2 = index2;
		return e;
	}

	private static Expression expressionConcat(String file, int line, Expression a, Expression b) {
		Expression e = new Expression();
		e.type = Expression.Type.CONCAT;
		e.file = file;
		e.line = line;
		e.exprA = a;
		e.exprB = b;
		return e;
	}

	private static Expression expressionJoin(String file, int line, Expression a, Expression b) {
		Expression e = new Expression();
		e.type = Expression.Type.JOIN;
		e.file = file;
		e.line = line;
		e.exprA = a;
		e.exprB = b;
		return e;
	}

	private static Expression expressionCalculator(String file, int line, Expression.CType type,
						       Expression a, Expression b) {
		Expression e = new Expression();
		e.type = Expression.Type.CALCULATOR;
		e.ctype = type;
		e.isUnary = false;
		e.file = file;
		e.line = line;
		e.exprA = a;
		e.exprB = b;
		return e;
	}

	private static Expression expressionCalculatorUnary(String file, int line, Expression.CType type,
							    Expression sub) {
		Expression e = new Expression();
		e.type = Expression.Type.CALCULATOR;
		e.ctype = type;
		e.isUnary = true;
		e.file = file;
		e.line = line;
		e.sub = sub;
		return e;
	}
	
	private static Expression parseExpression(TokenList tl) {
		Expression ans = parseExpressionJoin(tl);
		while (tl.isNext("..")) {
			String file = tl.seek().getFile();
			int line = tl.seek().getLine();
			tl.accept("..");
			ans = expressionConcat(file, line, ans, parseExpressionJoin(tl));
		}
		return ans;
	}
	
	private static Expression parseExpressionJoin(TokenList tl) {
		Expression ans = parseExpressionPrimary(tl);
		while (tl.isNext("&")) {
			String file = tl.seek().getFile();
			int line = tl.seek().getLine();
			tl.accept("&");
			ans = expressionJoin(file, line, ans, parseExpressionPrimary(tl));
		}
		return ans;
	}
       
	private static Expression parseExpressionPrimary(TokenList tl) {
		String file = tl.seek().getFile();
		int line = tl.seek().getLine();
		Expression ans;
		if (tl.isNext("'")) {
			tl.accept("'");
			String expr = tl.nextString();
			tl.accept("'");
			TokenList tlc = calculatorScanner.tokenize(expr, file, line);
			ans = parseCalculatorExpression(tlc);
			tlc.accept("<EOF>");
		}
		else if (tl.isNext("#")) {
			tl.accept("#");
			Expression e = parseExpressionPrimary(tl);
			return expressionLength(file, line, e);
		}
		else if (tl.isNext("{")) {
			List<Parameter> parameters = new ArrayList<>();
			boolean isVarargs = false;
			StreamType input = new ValueStream();
			StreamType output = new ValueStream();
			tl.accept("{");
			maybeNewline(tl);
			if (tl.isNext("|")) {
				tl.accept("|");
				while (!tl.isNext("|")) {
					parameters.add(parseParameter(tl));
					if (tl.isNext("...")) {
						tl.accept("...");
						isVarargs = true;
					}
				}
				tl.accept("|");
				newline(tl);
			}
			List<Statement> body = new ArrayList<>();
			while (!tl.isNext("}")) {
				body.add(parseStatement(tl));
				if (!tl.isNext("}"))
					newline(tl);
			}
			maybeNewline(tl);
			tl.accept("}");
			ans = expressionFunction(file, line, new Function("<block>", parameters, isVarargs, input, output, body));
		}
		else if (tl.isNext("!")) {
			tl.accept("!");
			tl.accept("(");
			maybeNewline(tl);
			Statement s = parseStatement(tl);
			maybeNewline(tl);
			tl.accept(")");
			ans = expressionStatement(file, line, s);
		}
		else if (tl.isNext("(")) {
			tl.accept("(");
			List<Expression> list = new ArrayList<>();
			while (!tl.isNext(")")) {
				list.add(parseExpression(tl));
			}
			tl.accept(")");
			ans = expressionList(file, line, list);
		}
		else if (tl.isNext("\"")) {
			tl.accept("\"");
			String s = tl.nextString();
			tl.accept("\"");
		        ans = expressionString(file, line, s);
		}
		else if (tl.seekString().matches("[0-9]+")) {
			ans = expressionInt(file, line, Integer.parseInt(tl.nextString()));
		}
		else if (tl.seekString().startsWith("-")) {
			ans = expressionString(file, line, tl.nextString());
		}
		else if (tl.isNext("<EOF>")) throw new ParsingException(TokenList.expected("#", "(", "{", "!", "<identifier>", "<number>", "<string>"), tl.next());
		else ans = expressionVariable(file, line, tl.nextString()); // TODO validointi

		ans = parseArrayAccessIfPossible(tl, ans);

		return ans;
	}

	private static Expression parseArrayAccessIfPossible(TokenList tl, Expression ans) {
		while (tl.isNext("[")) {
			String file = tl.seek().getFile();
			int line = tl.seek().getLine();
			tl.accept("[");
			Expression e1 = tl.isNext(":") ? null : parseExpression(tl);
			if (tl.isNext(":")) {
				tl.accept(":");
				Expression e2 = tl.isNext("]") ? null : parseExpression(tl);
				ans = expressionSlice(file, line, ans, e1, e2);
			}
			else ans = expressionElement(file, line, ans, e1);
			tl.accept("]");
		}
		return ans;
	}
	
	public static final TokenScanner calculatorScanner = new TokenScanner()
		.addOperatorRule("&&")
		.addOperatorRule("||")
		.addOperatorRule("^^")
		.addOperatorRule("!=")
		.addOperatorRule("<=")
		.addOperatorRule(">=")
		.addOperatorRule("<<")
		.addOperatorRule(">>")
		.addOperatorRule(">>>")
		.addOperators("#()[:]=+-*/<>&|^~!\n")
		.separateIdentifiersAndPunctuation(false)
		.addCommentRule("/*", "*/")
		.dontIgnore('\n')
		.appendOnEOF("<EOF>");
	
	private static OperatorLibrary<Expression> library;
	private static OperatorPrecedenceParser<Expression> opparser;

	private static BinaryOperator<Expression> op(Expression.CType type) {
		return (a, b) -> expressionCalculator(a.file, a.line, type, a, b);
	}
	
	static {
		/* Declares the operator library – all operators use parsePrimary() as their RHS parser */
		library = new OperatorLibrary<>(tl -> parseCalculatorPrimary(tl));
		
		/* Declares the operators*/
		library.add("&&", op(Expression.CType.AND));
		library.add("||", op(Expression.CType.OR));
		library.add("^^", op(Expression.CType.XOR));
		library.increaseLevel();
		library.add("=", op(Expression.CType.EQ));
		library.add("!=", op(Expression.CType.NEQ));
		library.increaseLevel();
		library.add("<", op(Expression.CType.LT));
		library.add(">", op(Expression.CType.GT));
		library.add("<=", op(Expression.CType.LE));
		library.add(">=", op(Expression.CType.GE));
		library.increaseLevel();
		library.add("&", op(Expression.CType.BAND));
		library.add("|", op(Expression.CType.BOR));
		library.add("^", op(Expression.CType.BXOR));
		library.add("<<", op(Expression.CType.BLSHIFT));
		library.add(">>", op(Expression.CType.BRSHIFT));
		library.add(">>>", op(Expression.CType.BRRSHIFT));
		library.increaseLevel();
		library.add("+", op(Expression.CType.ADD));
		library.add("-", op(Expression.CType.SUB));
		library.increaseLevel();
		library.add("*", op(Expression.CType.MUL));
		library.add("/", op(Expression.CType.DIV));
		
		/* Declares the OPP*/
		opparser = OperatorPrecedenceParser.fromLibrary(library);
	}
	
	private static Expression parseCalculatorExpression(TokenList tl) {
		Expression e = opparser.parse(tl);
		return e;
	}

	private static Expression parseCalculatorPrimary(TokenList tl) {
		String file = tl.seek().getFile();
		int line = tl.seek().getLine();
		Expression ans;
		if (tl.isNext("-")) {
			tl.accept("-");
			return expressionCalculatorUnary(file, line, Expression.CType.NEG,
							 parseCalculatorPrimary(tl));
		}
		if (tl.isNext("~")) {
			tl.accept("~");
			return expressionCalculatorUnary(file, line, Expression.CType.BNOT,
							 parseCalculatorPrimary(tl));
		}
		if (tl.isNext("!")) {
			tl.accept("!");
			return expressionCalculatorUnary(file, line, Expression.CType.NOT,
							 parseCalculatorPrimary(tl));
		}
		if (tl.isNext("#")) {
			tl.accept("#");
			return expressionLength(file, line, parseCalculatorPrimary(tl));
		}
		if (tl.isNext("(")) {
			tl.accept("(");
			Expression e = parseCalculatorExpression(tl);
			tl.accept(")");
			ans = e;
		}
		else if (tl.seekString().matches("[0-9]+")) {
			ans = expressionInt(file, line, Integer.parseInt(tl.nextString()));
		}
		else {
		        ans = expressionVariable(file, line, tl.nextString());
		}

		ans = parseArrayAccessIfPossible(tl, ans);

		return ans;
	}
}
