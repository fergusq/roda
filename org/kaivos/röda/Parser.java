package org.kaivos.röda;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import java.util.regex.Pattern;

import org.kaivos.nept.parser.TokenList;
import org.kaivos.nept.parser.TokenScanner;
import org.kaivos.nept.parser.ParsingException;

import org.kaivos.röda.Interpreter.StreamType;
import org.kaivos.röda.Interpreter.ValueStream;
import org.kaivos.röda.Interpreter.ByteStream;
import org.kaivos.röda.Interpreter.LineStream;
import org.kaivos.röda.Interpreter.VoidStream;

public class Parser {
	public static final TokenScanner t = new TokenScanner()
		.addOperatorRule("...")
		.addOperatorRule("..")
		.addOperatorRule("->")
		.addOperators("<>()[]{}|&.:;=#%\n")
		.addPatternRule(Pattern.compile("[0-9]+\\.[0-9]+"))
		.separateIdentifiersAndPunctuation(false)
		.addCommentRule("/*", "*/")
		.addStringRule('"','"','\\')
		.addEscapeCode('\\', "\\")
		.addEscapeCode('n', "\n")
		.addEscapeCode('t', "\t")
		.addCharacterEscapeCode('x', 2, 16)
		.dontIgnore('\n') // TODO
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
		List<Statement> body;
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

	static Command _makeWhileCommand(String file, int line, Statement cond, List<Statement> body) {
		Command cmd = new Command();
		cmd.type = Command.Type.WHILE;
		cmd.file = file;
		cmd.line = line;
		cmd.cond = cond;
		cmd.body = body;
		return cmd;
	}

	static Command _makeIfCommand(String file, int line, Statement cond, List<Statement> body) {
		Command cmd = new Command();
		cmd.type = Command.Type.IF;
		cmd.file = file;
		cmd.line = line;
		cmd.cond = cond;
		cmd.body = body;
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
		if (tl.isNext("while")) {
			tl.accept("while");
			Statement cond = parseStatement(tl);
			maybeNewline(tl);
			tl.accept("do");
			maybeNewline(tl);
			List<Statement> body = new ArrayList<>();
			while (!tl.isNext("done")) {
				body.add(parseStatement(tl));
				newline(tl);
			}
			tl.accept("done");
			return _makeWhileCommand(file, line, cond, body);
		}
		if (tl.isNext("if")) {
			tl.accept("if");
			Statement cond = parseStatement(tl);
			maybeNewline(tl);
			tl.accept("do");
			maybeNewline(tl);
			List<Statement> body = new ArrayList<>();
			while (!tl.isNext("done")) {
				body.add(parseStatement(tl));
				newline(tl);
			}
			tl.accept("done");
			return _makeIfCommand(file, line, cond, body);
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
			JOIN
		}
		Type type;
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
	
	static Expression parseExpression(TokenList tl) {
		Expression ans = parseExpressionConcat(tl);
		while (tl.isNext("&")) {
			String file = tl.seek().getFile();
			int line = tl.seek().getLine();
			tl.accept("&");
			ans = expressionJoin(file, line, ans, parseExpressionPrimary(tl));
		}
		return ans;
	}
	
	static Expression parseExpressionConcat(TokenList tl) {
		Expression ans = parseExpressionPrimary(tl);
		while (tl.isNext("..")) {
			String file = tl.seek().getFile();
			int line = tl.seek().getLine();
			tl.accept("..");
			ans = expressionConcat(file, line, ans, parseExpressionPrimary(tl));
		}
		return ans;
	}
	
	static Expression parseExpressionPrimary(TokenList tl) {
		String file = tl.seek().getFile();
		int line = tl.seek().getLine();
		Expression ans;
		if (tl.isNext("#")) {
			tl.accept("#");
			Expression e = parseExpression(tl);
			ans = expressionLength(file, line, e);
		}
		else if (tl.isNext("{")) {
			StreamType input = new ValueStream();
			StreamType output = new ValueStream();
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
			ans = expressionFunction(file, line, new Function("<block>", new ArrayList<>(), false, input, output, body));
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

		while (tl.isNext("[")) {
			file = tl.seek().getFile();
			line = tl.seek().getLine();
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
}
