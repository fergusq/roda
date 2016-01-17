package org.kaivos.röda;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import java.util.regex.Pattern;

import java.util.function.BinaryOperator;

import static java.util.stream.Collectors.joining;

import org.kaivos.nept.parser.Token;
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
		.addOperatorRule(":=")
		.addOperatorRule("~=")
		.addOperatorRule(".=")
		.addOperatorRule("+=")
		.addOperatorRule("-=")
		.addOperatorRule("*=")
		.addOperatorRule("/=")
		.addOperatorRule("++")
		.addPatternRule(Pattern.compile("--(?!\\p{L})"))
		.addOperators("<>()[]{}|&.,:;=#%!?\n\\*")
		.separateIdentifiersAndPunctuation(false)
		.addCommentRule("/*", "*/")
		.addStringRule('\'', '\'', (char) 0)
		.addStringRule("[[", "]]", (char) 0)
		.addStringRule('"','"','\\')
		.addEscapeCode('\\', "\\")
		.addEscapeCode('n', "\n")
		.addEscapeCode('r', "\r")
		.addEscapeCode('t', "\t")
		.addCharacterEscapeCode('x', 2, 16)
		.ignore("\\\n")
		.dontIgnore('\n')
		.appendOnEOF("<EOF>");

	private static boolean validIdentifier(String applicant) {
		if (!validTypename(applicant)) return false;
		switch (applicant) {
		case "function":
		case "list":
		case "map":
		case "string":
		case "number":
		case "boolean":
			return false;
		default:
			return true;
		}
	}

	private static boolean validTypename(String applicant) {
		switch (applicant) {
		case "if":
		case "while":
		case "else":
		case "for":
		case "do":
		case "done":
		case "record":
		case "value":
		case "new":
		case "reflect":
		case "typeof":
		case "_character":
		case "_line":
		case "_value":
		case "void":
		case "reference":
			return false;
		default:
			return true;
		}
	}

	private static String identifier(TokenList tl) {
	        Token token = tl.next();
		if (!validIdentifier(token.getToken()))
			throw new ParsingException(TokenList.expected("identifier"),
						   token);
		return token.getToken();
	}

	private static String typename(TokenList tl) {
	        Token token = tl.next();
		if (!validTypename(token.getToken()))
			throw new ParsingException(TokenList.expected("typename"),
						   token);
		return token.getToken();
	}
	
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

	// TODO tee parempi toteutus tyypeille
	// Ongelmat:
	// - RödaValue sisältää kaikki tyypit - HUONO
	// - Datatype vs. RödaValue.Type - ei monia luokkia samaan asiaan
	public static class Datatype {
		public final String name;
		public final List<Datatype> subtypes;

		public Datatype(String name,
				List<Datatype> subtypes) {
			this.name = name;
			this.subtypes = Collections.unmodifiableList(subtypes);
		}

		public Datatype(String name) {
			this.name = name;
			this.subtypes = Collections.emptyList();
		}

		@Override
		public String toString() {
			if (subtypes.isEmpty())
				return name;
			return name + "<" + subtypes.stream()
				.map(Datatype::toString)
				.collect(joining(", ")) + ">";
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Datatype))
				return false;
			Datatype other = (Datatype) obj;
			if (!name.equals(other.name))
				return false;
			if (!subtypes.equals(other.subtypes))
				return false;

			return true;
		}
	}

	static Datatype parseType(TokenList tl) {
		String name = typename(tl);
		List<Datatype> subtypes = new ArrayList<>();
	        if (!name.equals("function")
		    && !name.equals("string")
		    && !name.equals("boolean")
		    && !name.equals("number")
		    && tl.acceptIfNext("<")) {
			do {
				subtypes.add(parseType(tl));
			} while (tl.acceptIfNext(","));
			tl.accept(">");
		}
		return new Datatype(name, subtypes);
	}
	
	static class Program {
		List<Function> functions;
		List<Record> records;
		Program(List<Function> functions,
			List<Record> records) {
			this.functions = functions;
			this.records = records;
		}
	}
	
	static Program parse(TokenList tl) {
		//System.err.println(tl);
		if (tl.acceptIfNext("#")) { // purkkaa, tee tämä lekseriin
			while (!tl.nextString().equals("\n"));
		}

		maybeNewline(tl);

		List<Function> functions = new ArrayList<>();
		List<Record> records = new ArrayList<>();
		while (!tl.isNext("<EOF>")) {
			List<Annotation> annotations = parseAnnotations(tl);
			if (tl.isNext("record")) {
				records.add(parseRecord(tl, annotations));
			}
			else {
				functions.add(parseFunction(tl, true));
			}
			maybeNewline(tl);
		}
		return new Program(functions, records);
	}

	public static class Annotation {
		public final String name;
		public final List<Argument> args;
		final String file;
		final int line;
		public Annotation(String file, int line, String name, List<Argument> args) {
			this.file = file;
			this.line = line;
			this.name = name;
			this.args = args;
		}
	}

	private static List<Annotation> parseAnnotations(TokenList tl) {
		List<Annotation> annotations = new ArrayList<>();
		while (tl.acceptIfNext("@")) {
			String file = tl.seek().getFile();
			int line = tl.seek().getLine();
			String name = "@" + identifier(tl);
			List<Argument> arguments = parseArguments(tl, false);
			annotations.add(new Annotation(file, line, name, arguments));
			newline(tl);
		}
		return annotations;
	}

	public static class Record {
		public static class Field {
			public final String name;
			public final Datatype type;
			final Expression defaultValue;
			public final List<Annotation> annotations;

			Field(String name,
			      Datatype type) {
				this(name, type, null, Collections.emptyList());
			}

			Field(String name,
			      Datatype type,
			      Expression defaultValue,
			      List<Annotation> annotations) {
				this.name = name;
				this.type = type;
				this.defaultValue = defaultValue;
				this.annotations = Collections.unmodifiableList(annotations);
			}
		}

		public final String name;
		public final List<String> typeparams;
		public final Datatype superType;
		public final List<Annotation> annotations;
		public final List<Field> fields;
		public final boolean isValueType;

		Record(String name,
		       List<String> typeparams,
		       Datatype superType,
		       List<Field> fields,
		       boolean isValueType) {
			this(name, typeparams, superType, Collections.emptyList(),
			     fields, isValueType);
		}

		Record(String name,
		       List<String> typeparams,
		       Datatype superType,
		       List<Annotation> annotations,
		       List<Field> fields,
		       boolean isValueType) {
			this.name = name;
			this.typeparams = Collections.unmodifiableList(typeparams);
			this.annotations = Collections.unmodifiableList(annotations);
			this.fields = Collections.unmodifiableList(fields);
			this.superType = superType;
			this.isValueType = isValueType;
		}
	}

	static Record parseRecord(TokenList tl, List<Annotation> recordAnnotations) {
		tl.accept("record");

		boolean isValueType = tl.acceptIfNext("value");

		String name = identifier(tl);
		List<String> typeparams = parseTypeparameters(tl);

		maybeNewline(tl);

		Datatype superType = null;
		if (tl.acceptIfNext(":")) {
			superType = parseType(tl);

			maybeNewline(tl);
		}

		List<Record.Field> fields = new ArrayList<>();

		tl.accept("{");
		maybeNewline(tl);
		while (!tl.isNext("}")) {
			List<Annotation> annotations = parseAnnotations(tl);
			if (tl.isNext("function")) {
				String file = tl.seek().getFile();
				int line = tl.seek().getLine();
				Function method = parseFunction(tl, false);
				String fieldName = method.name;
				Datatype type = new Datatype("function");
				Expression defaultValue = expressionFunction(file, line, method);
				fields.add(new Record.Field(fieldName, type, defaultValue, annotations));
			}
			else {
				String fieldName = identifier(tl);
				tl.accept(":");
				Datatype type = parseType(tl);
				Expression defaultValue = null;
				if (tl.acceptIfNext("=")) {
					defaultValue = parseExpression(tl);
				}
				fields.add(new Record.Field(fieldName, type, defaultValue, annotations));
			}
			if (!tl.isNext("}"))
				newline(tl);
		}
		maybeNewline(tl);
		tl.accept("}");

		return new Record(name, typeparams, superType, recordAnnotations, fields, isValueType);
	}

	public static class Function {
		public String name;
		public List<String> typeparams;
		public List<Parameter> parameters;
		public boolean isVarargs;
		public StreamType input;
		public StreamType output;
		public List<Statement> body;

		Function(String name,
			 List<String> typeparams,
			 List<Parameter> parameters,
			 boolean isVarargs,
			 StreamType input,
			 StreamType output,
			 List<Statement> body) {

			this.name = name;
			this.typeparams = typeparams;
			this.parameters = parameters;
			this.isVarargs = isVarargs;
			this.input = input;
			this.output = output;
			this.body = body;
		}
	}

	public static class Parameter {
		String name;
		boolean reference;
	        Parameter(String name, boolean reference) {
			this.name = name;
			this.reference = reference;
		}
	}

	static Parameter parseParameter(TokenList tl) {
		boolean reference = false;
		if (tl.acceptIfNext("&")) {
			reference = true;
		}
		String name = tl.nextString();
		return new Parameter(name, reference);
	}
	
	static Function parseFunction(TokenList tl, boolean mayBeAnnotation) {
		tl.acceptIfNext("function");

		boolean isAnnotation = mayBeAnnotation ? tl.acceptIfNext("@") : false;
		String name = (isAnnotation ? "@" : "") + identifier(tl);
		List<String> typeparams = parseTypeparameters(tl);

		maybeNewline(tl);

		List<Parameter> parameters = new ArrayList<>();
		boolean isVarargs = false;
		while (!tl.isNext(":") && !tl.isNext("{")) {
			parameters.add(parseParameter(tl));
			if (!isVarargs && tl.acceptIfNext("...")) {
				isVarargs = true;
			}
			maybeNewline(tl);
		}

		StreamType input, output;
		if (tl.acceptIfNext(":")) {
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
			body.add(parseStatement(tl, false));
			if (!tl.isNext("}"))
				newline(tl);
		}
		maybeNewline(tl);
		tl.accept("}");

		return new Function(name, typeparams, parameters, isVarargs, input, output, body);
	}

	static List<String> parseTypeparameters(TokenList tl) {
		List<String> typeparams = new ArrayList<>();
		if (tl.acceptIfNext("<")) {
			do {
				maybeNewline(tl);
				String typeparam = identifier(tl);
				typeparams.add(typeparam);
			} while (tl.acceptIfNext(","));
			maybeNewline(tl);
			tl.accept(">");
		}
		return typeparams;
	}
	
	static StreamType parseStreamType(TokenList tl) {
		if (tl.acceptIfNext("void")) {
			return new VoidStream();
		}
		if (tl.acceptIfNext("_character")) {
			return new ByteStream();
		}
		if (tl.acceptIfNext("_line")) {
			if (tl.isNext("(")) {
				tl.accept(")");
				tl.accept("\"");
				String sep = tl.nextString();
				tl.accept("\"");
				return new LineStream(sep);
			}
			return new LineStream();
		}
		if (tl.acceptIfNext("_value")) {
			return new ValueStream();
		}

		throw new ParsingException(TokenList.expected("_character", "_line", "void", "_value"), tl.next());
	}

	static class Statement {
		List<Command> commands;
		Statement(List<Command> commands) {
			this.commands = commands;
		}
	}

	static Statement parseStatement(TokenList tl, boolean acceptNewlines) {
		List<Command> commands = new ArrayList<>();
		commands.add(parseCommand(tl, acceptNewlines));
		while (tl.acceptIfNext("|")) {
			maybeNewline(tl);
			commands.add(parseCommand(tl, acceptNewlines));
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
			TRY_DO,
			VARIABLE,
			RETURN,
			BREAK,
			CONTINUE
		}
		Type type;
		Expression name;
		String operator;
		List<Datatype> typearguments;
		List<Argument> arguments;
		Statement cond;
		String variable;
		Expression list;
		List<Statement> body, elseBody;
		Command cmd;
		Command() {} // käytä apufunktioita alla
		String file;
		int line;
	}

	static class Argument {
		boolean flattened;
		Expression expr;
	}

	static Argument _makeArgument(boolean flattened, Expression expr) {
		Argument arg = new Argument();
		arg.flattened = flattened;
		arg.expr = expr;
		return arg;
	}
	
	static Command _makeNormalCommand(String file, int line, Expression name,
					  List<Datatype> typearguments,
					  List<Argument> arguments) {
		Command cmd = new Command();
		cmd.type = Command.Type.NORMAL;
		cmd.file = file;
		cmd.line = line;
		cmd.name = name;
		cmd.typearguments = typearguments;
		cmd.arguments = arguments;
		return cmd;
	}
	
	static Command _makeVariableCommand(String file, int line, Expression name,
					    String operator, List<Argument> arguments) {
		Command cmd = new Command();
		cmd.type = Command.Type.VARIABLE;
		cmd.file = file;
		cmd.line = line;
		cmd.name = name;
		cmd.operator = operator;
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
	
	static Command _makeForCommand(String file, int line, String variable,
				       Expression list, List<Statement> body) {
		Command cmd = new Command();
		cmd.type = Command.Type.FOR;
		cmd.file = file;
		cmd.line = line;
		cmd.variable = variable;
		cmd.list = list;
		cmd.body = body;
		return cmd;
	}

	static Command _makeTryCommand(String file, int line, List<Statement> body,
				       String catchVar, List<Statement> elseBody) {
		Command cmd = new Command();
		cmd.type = Command.Type.TRY_DO;
		cmd.file = file;
		cmd.line = line;
		cmd.body = body;
		cmd.variable = catchVar;
		cmd.elseBody = elseBody;
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
	
	static Command _makeReturnCommand(String file, int line, List<Argument> arguments) {
		Command cmd = new Command();
		cmd.type = Command.Type.RETURN;
		cmd.file = file;
		cmd.line = line;
		cmd.arguments = arguments;
		return cmd;
	}
	
	static Command _makeBreakCommand(String file, int line) {
		Command cmd = new Command();
		cmd.type = Command.Type.BREAK;
		cmd.file = file;
		cmd.line = line;
		return cmd;
	}
	
	static Command _makeContinueCommand(String file, int line) {
		Command cmd = new Command();
		cmd.type = Command.Type.CONTINUE;
		cmd.file = file;
		cmd.line = line;
		return cmd;
	}
	
	static Command parseCommand(TokenList tl, boolean acceptNewlines) {
		String file = tl.seek().getFile();
		int line = tl.seek().getLine();
		if (tl.isNext("while") || tl.isNext("if")) {
			boolean isWhile = tl.nextString().equals("while");
			Statement cond = parseStatement(tl, true);
			newline(tl);
			tl.accept("do");
			maybeNewline(tl);
			List<Statement> body = new ArrayList<>(), elseBody = null;
			while (!tl.isNext("done") && !tl.isNext("else")) {
				body.add(parseStatement(tl, false));
				newline(tl);
			}
			if (tl.isNext("else")) {
				tl.accept("else");
				maybeNewline(tl);
				elseBody = new ArrayList<>();
				while (!tl.isNext("done")) {
					elseBody.add(parseStatement(tl, false));
					newline(tl);
				}
			}
			tl.accept("done");
			return _makeIfOrWhileCommand(file, line, isWhile, cond, body, elseBody);
		}

		if (tl.acceptIfNext("for")) {
			String variable = tl.nextString();
			tl.accept("in");
			Expression list = parseExpression(tl);
			maybeNewline(tl);
			tl.accept("do");
			maybeNewline(tl);
			List<Statement> body = new ArrayList<>();
			while (!tl.isNext("done")) {
				body.add(parseStatement(tl, false));
				newline(tl);
			}
			tl.accept("done");
			return _makeForCommand(file, line, variable, list, body);
		}

		if (tl.acceptIfNext("try")) {
			maybeNewline(tl);
			if (tl.isNext("do")) {
				tl.accept("do");
				maybeNewline(tl);
				List<Statement> body = new ArrayList<>();
				while (!tl.isNext("catch", "done")) {
					body.add(parseStatement(tl, false));
					newline(tl);
				}
				String catchVar = null;
				List<Statement> elseBody = null;
				if (tl.acceptIfNext("catch")) {
					catchVar = identifier(tl);
					newline(tl);
					elseBody = new ArrayList<>();
					while (!tl.isNext("done")) {
						elseBody.add(parseStatement(tl, false));
						newline(tl);
					}
				}
				tl.accept("done");
				return _makeTryCommand(file, line, body, catchVar, elseBody);
			} else {
				return _makeTryCommand(file, line, parseCommand(tl, acceptNewlines));
			}
		}

		if (tl.acceptIfNext("return")) {
			List<Argument> arguments = parseArguments(tl, acceptNewlines);
			return _makeReturnCommand(file, line, arguments);
		}

		if (tl.acceptIfNext("break")) {
			return _makeBreakCommand(file, line);
		}

		if (tl.acceptIfNext("continue")) {
			return _makeContinueCommand(file, line);
		}

		Expression name = parseExpression(tl);
		String operator = null;
		List<Datatype> typeargs = new ArrayList<>();
		if (tl.isNext(":=", "=", "++", "--", "+=", "-=", "*=", "/=", ".=", "~=", "?")) {
			operator = tl.nextString();
		}
		else if (tl.acceptIfNext("<")) {
			do {
				maybeNewline(tl);
				typeargs.add(parseType(tl));
			} while (tl.acceptIfNext(","));
			maybeNewline(tl);
			tl.accept(">");
		}
		
		List<Argument> arguments = parseArguments(tl, acceptNewlines);
		if (operator == null)
			return _makeNormalCommand(file, line, name, typeargs, arguments);
		else
			return _makeVariableCommand(file, line, name, operator, arguments);
	}

	private static List<Argument> parseArguments(TokenList tl, boolean acceptNewlines) {
		List<Argument> arguments = new ArrayList<>();
		if (acceptNewlines
			    && !(tl.isNext("\n", ";") && tl.seekString(1).equals("do"))) maybeNewline(tl);
		while (!tl.isNext("|", ";", "\n", ")", "]", "}", "in", "<EOF>")) {
			boolean flattened = tl.acceptIfNext("*");
			arguments.add(_makeArgument(flattened,
						    parseExpression(tl)));
			if (acceptNewlines
			    && !(tl.isNext("\n", ";") && tl.seekString(1).equals("do"))) maybeNewline(tl);
		}
		return arguments;
	}
	
	static class Expression {
		enum Type {
			VARIABLE,
			STRING,
			NUMBER,
			STATEMENT_LIST,
			STATEMENT_SINGLE,
			BLOCK,
			LIST,
			LENGTH,
			ELEMENT,
			CONTAINS,
			FIELD,
			SLICE,
			CONCAT,
			JOIN,
			CALCULATOR,
			NEW,
			REFLECT,
			TYPEOF
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
			MATCHES,
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
		String field;
		Datatype datatype;

		String file;
		int line;

		String asString() {
			switch (type) {
			case VARIABLE:
				return variable;
			case STRING:
				return "\"" + string.replaceAll("\\\\", "\\\\").replaceAll("\"", "\\\"") + "\"";
			case NUMBER:
				return String.valueOf(number);
			case SLICE: {
				String i1 = index1 == null ? "" : index1.asString();
				String i2 = index2 == null ? "" : index2.asString();
				return sub.asString() + "[" + i1 + ":" + i2 + "]";
			}
			case BLOCK:
				return "{...}";
			case LIST:
				return "(...)";
			case STATEMENT_LIST:
				return "!(...)";
			case STATEMENT_SINGLE:
				return "![...]";
			case CALCULATOR:
				return "'...'";
			case ELEMENT:
				return sub.asString() + "[" + index.asString() + "]";
			case CONTAINS:
				return sub.asString() + "[" + index.asString() + "]?";
			case FIELD:
				return sub.asString() + "." + field;
			case LENGTH:
				return "#" + sub.asString();
			case CONCAT:
				return exprA.asString() + ".." + exprB.asString();
			case JOIN:
				return exprA.asString() + "&" + exprB.asString();
			case NEW:
				return "new " + datatype.toString();
			case REFLECT:
				return "reflect " + datatype.toString();
			case TYPEOF:
				return "typeof " + sub.asString();
			default:
				return "<" + type + ">";
			}
		}
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

	private static Expression expressionStatementList(String file, int line, Statement statement) {
		Expression e = new Expression();
		e.type = Expression.Type.STATEMENT_LIST;
		e.file = file;
		e.line = line;
		e.statement = statement;
		return e;
	}

	private static Expression expressionStatementSingle(String file, int line, Statement statement) {
		Expression e = new Expression();
		e.type = Expression.Type.STATEMENT_SINGLE;
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

	private static Expression expressionContains(String file, int line, Expression list, Expression index) {
		Expression e = new Expression();
		e.type = Expression.Type.CONTAINS;
		e.file = file;
		e.line = line;
		e.sub = list;
		e.index = index;
		return e;
	}

	private static Expression expressionField(String file, int line, Expression list, String field) {
		Expression e = new Expression();
		e.type = Expression.Type.FIELD;
		e.file = file;
		e.line = line;
		e.sub = list;
		e.field = field;
		return e;
	}

	private static Expression expressionSlice(String file, int line,
						  Expression list, Expression index1, Expression index2) {
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

	private static Expression expressionNew(String file, int line, Datatype datatype) {
		Expression e = new Expression();
		e.type = Expression.Type.NEW;
		e.file = file;
		e.line = line;
		e.datatype = datatype;
		return e;
	}

	private static Expression expressionReflect(String file, int line, Datatype datatype) {
		Expression e = new Expression();
		e.type = Expression.Type.REFLECT;
		e.file = file;
		e.line = line;
		e.datatype = datatype;
		return e;
	}

	private static Expression expressionTypeof(String file, int line, Expression sub) {
		Expression e = new Expression();
		e.type = Expression.Type.TYPEOF;
		e.file = file;
		e.line = line;
		e.sub = sub;
		return e;
	}
	
	private static Expression parseExpression(TokenList tl) {
		Expression ans = parseExpressionJoin(tl);
		while (tl.acceptIfNext("..")) {
			String file = tl.seek().getFile();
			int line = tl.seek().getLine();
			ans = expressionConcat(file, line, ans, parseExpressionJoin(tl));
		}
		return ans;
	}
	
	private static Expression parseExpressionJoin(TokenList tl) {
		Expression ans = parseExpressionPrimary(tl);
		while (tl.acceptIfNext("&")) {
			String file = tl.seek().getFile();
			int line = tl.seek().getLine();
			ans = expressionJoin(file, line, ans, parseExpressionPrimary(tl));
		}
		return ans;
	}
	
	private static Expression parseExpressionPrimary(TokenList tl) {
		String file = tl.seek().getFile();
		int line = tl.seek().getLine();
		Expression ans;
		if (tl.acceptIfNext("'")) {
			String expr = tl.nextString();
			tl.accept("'");
			TokenList tlc = calculatorScanner.tokenize(expr, file, line);
			ans = parseCalculatorExpression(tlc);
			tlc.accept("<EOF>");
		}
		else if (tl.acceptIfNext("new")) {
			Datatype type = parseType(tl);
			ans = expressionNew(file, line, type);
		}
		else if (tl.acceptIfNext("reflect")) {
			Datatype type = parseType(tl);
			ans = expressionReflect(file, line, type);
		}
		else if (tl.acceptIfNext("typeof")) {
			Expression e = parseExpressionPrimary(tl);
			return expressionTypeof(file, line, e);
		}
		else if (tl.acceptIfNext("#")) {
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
				body.add(parseStatement(tl, false));
				if (!tl.isNext("}"))
					newline(tl);
			}
			maybeNewline(tl);
			tl.accept("}");
			ans = expressionFunction(file, line, new Function("<block>", Collections.emptyList(), parameters,
									  isVarargs, input, output, body));
		}
		else if (tl.acceptIfNext("!")) {
			if (tl.acceptIfNext("(")) {
				maybeNewline(tl);
				Statement s = parseStatement(tl, true);
				maybeNewline(tl);
				tl.accept(")");
				ans = expressionStatementList(file, line, s);
			}
			else if (tl.acceptIfNext("[")) {
				maybeNewline(tl);
				Statement s = parseStatement(tl, true);
				maybeNewline(tl);
				tl.accept("]");
				ans = expressionStatementSingle(file, line, s);
			}
			else {
				ans = null;
				assert false;
			}
		}
		else if (tl.acceptIfNext("(")) {
			List<Expression> list = new ArrayList<>();
			while (!tl.isNext(")")) {
				list.add(parseExpression(tl));
			}
			tl.accept(")");
			ans = expressionList(file, line, list);
		}
		else if (tl.acceptIfNext("\"")) {
			String s = tl.nextString();
			tl.accept("\"");
		        ans = expressionString(file, line, s);
		}
		else if (tl.acceptIfNext("[[")) {
			String s = tl.nextString();
			tl.accept("]]");
		        ans = expressionString(file, line, s);
		}
		else if (tl.seekString().matches("[0-9]+")) {
			ans = expressionInt(file, line, Integer.parseInt(tl.nextString()));
		}
		else if (tl.seekString().startsWith("-")) {
			ans = expressionString(file, line, tl.nextString());
		}
		else if (tl.isNext("<EOF>")) throw new ParsingException(TokenList.expected("#", "(", "{", "!", "<identifier>", "<number>", "<string>"), tl.next());
		else {
			String name = identifier(tl);
			ans = expressionVariable(file, line, name);
		}

		ans = parseArrayAccessIfPossible(tl, ans, Parser::parseExpression);

		return ans;
	}

	private static Expression parseArrayAccessIfPossible(TokenList tl, Expression ans,
							     java.util.function.
							     Function<TokenList, Expression> expressionParser) {
		while (tl.isNext("[", ".")) {
			String file = tl.seek().getFile();
			int line = tl.seek().getLine();
			if (tl.acceptIfNext("[")) {
				Expression e1 = tl.isNext(":") ? null : expressionParser.apply(tl);
				if (tl.isNext(":")) {
					tl.accept(":");
					Expression e2 = tl.isNext("]") ? null : expressionParser.apply(tl);
					tl.accept("]");
					ans = expressionSlice(file, line, ans, e1, e2);
				}
				else {
					tl.accept("]");
					if (tl.acceptIfNext("?")) {
						ans = expressionContains(file, line, ans, e1);
					}
					else {
						ans = expressionElement(file, line, ans, e1);
					}
				}
			}
			else if (tl.acceptIfNext(".")) {
				String field = identifier(tl);
				ans = expressionField(file, line, ans, field);
			}
			else assert false;
		}
		return ans;
	}
	
	public static final TokenScanner calculatorScanner = new TokenScanner()
		.addOperatorRule("&&")
		.addOperatorRule("||")
		.addOperatorRule("^^")
		.addOperatorRule("!=")
		.addOperatorRule("=~")
		.addOperatorRule("<=")
		.addOperatorRule(">=")
		.addOperatorRule("<<")
		.addOperatorRule(">>")
		.addOperatorRule(">>>")
		.addOperators("#()[:].=+-*/<>&|^~!\n")
		.separateIdentifiersAndPunctuation(false)
		.addCommentRule("/*", "*/")
		.addStringRule("[[", "]]", (char) 0)
		.addStringRule('"','"','\\')
		.addEscapeCode('\\', "\\")
		.addEscapeCode('n', "\n")
		.addEscapeCode('r', "\r")
		.addEscapeCode('t', "\t")
		.addCharacterEscapeCode('x', 2, 16)
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
		library.add("=~", op(Expression.CType.MATCHES));
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
		else if (tl.acceptIfNext("\"")) {
			String s = tl.nextString();
			tl.accept("\"");
		        ans = expressionString(file, line, s);
		}
		else if (tl.acceptIfNext("[[")) {
			String s = tl.nextString();
			tl.accept("]]");
		        ans = expressionString(file, line, s);
		}
		else if (tl.seekString().matches("[0-9]+")) {
			ans = expressionInt(file, line, Integer.parseInt(tl.nextString()));
		}
	        else {
			String name = identifier(tl);
			ans = expressionVariable(file, line, name);
		}

		ans = parseArrayAccessIfPossible(tl, ans, Parser::parseCalculatorExpression);

		return ans;
	}
}
