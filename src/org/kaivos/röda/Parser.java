package org.kaivos.röda;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.function.BinaryOperator;

import static java.util.stream.Collectors.joining;

import org.kaivos.nept.parser.Token;
import org.kaivos.nept.parser.TokenList;
import org.kaivos.nept.parser.TokenScanner;
import org.kaivos.nept.parser.OperatorLibrary;
import org.kaivos.nept.parser.OperatorPrecedenceParser;
import org.kaivos.nept.parser.ParsingException;

public class Parser {

	private static final String NUMBER_REGEX = "-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE](\\+|-)?[0-9]+)";
	
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
		.addOperatorRule("--")
		.addOperatorRule("//")
		.addOperatorRule("&&")
		.addOperatorRule("||")
		.addOperatorRule("^^")
		.addOperatorRule("!=")
		.addOperatorRule("=~")
		.addOperatorRule("<=")
		.addOperatorRule(">=")
		.addOperatorRule("<<")
		.addOperatorRule(">>")
		.addPatternRule(Pattern.compile(NUMBER_REGEX))
		.addOperators("<>()[]{}|&.,:;=#%!?\n\\+-*/~@%$")
		.separateIdentifiersAndPunctuation(false)
		.addCommentRule("/*", "*/")
		.addStringRule('"','"','\\')
		.addEscapeCode('\\', "\\")
		.addEscapeCode('n', "\n")
		.addEscapeCode('r', "\r")
		.addEscapeCode('t', "\t")
		.ignore("\\\n")
		.dontIgnore('\n')
		.addCharacterEscapeCode('x', 2, 16)
		.appendOnEOF("<EOF>");

	private static Token seek(TokenList tl) {
		int i = 1;
		while (tl.has(i)) {
			if (!tl.seekString(i-1).equals("\n") && !tl.seekString(i-1).equals(";")) return tl.seek(i-1);
			i++;
		}
		throw new RuntimeException();
	}

	private static String seekString(TokenList tl) {
		return seek(tl).getToken();
	}
	
	public static boolean isNext(TokenList tl, String... keyword) {
		return Arrays.asList(keyword).contains(seek(tl).getToken());
	}
	
	private static void skipNewlines(TokenList tl) {
		while (tl.acceptIfNext(";", "\n")) {}
	}

	private static Token next(TokenList tl) {
		skipNewlines(tl);
		return tl.next();
	}

	private static String nextString(TokenList tl) {
		skipNewlines(tl);
		return tl.nextString();
	}

	private static boolean acceptIfNext(TokenList tl, String... keywords) {
		if (isNext(tl, keywords)) {
			skipNewlines(tl);
			tl.accept(keywords);
			return true;
		}
		return false;
	}

	private static void accept(TokenList tl, String... keywords) {
		skipNewlines(tl);
		tl.accept(keywords);
	}
	
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
		if (applicant.matches("[<>()\\[\\]{}|&.,:;=#%!?\n+\\-*/~@%$_]|[:~.+\\-*/!]=|\\+\\+|&&|\\|\\||^^|=~|<=|>=|<<|>>")) return false;
		switch (applicant) {
		case "if":
		case "unless":
		case "while":
		case "until":
		case "else":
		case "for":
		case "in":
		case "do":
		case "done":
		case "break":
		case "continue":
		case "return":
		case "record":
		case "value":
		case "new":
		case "reflect":
		case "typeof":
		case "is":
		case "reference":
		case "not":
		case "and":
		case "or":
		case "xor":
			return false;
		default:
			return true;
		}
	}

	private static String varname(TokenList tl) {
	        Token token = next(tl);
		if (!validIdentifier(token.getToken()) && !token.getToken().equals("_"))
			throw new ParsingException(TokenList.expected("identifier or _"),
						   token);
		return token.getToken();
	}

	private static String identifier(TokenList tl) {
	        Token token = next(tl);
		if (!validIdentifier(token.getToken()))
			throw new ParsingException(TokenList.expected("identifier"),
						   token);
		return token.getToken();
	}

	private static String typename(TokenList tl) {
	        Token token = next(tl);
		if (!validTypename(token.getToken()))
			throw new ParsingException(TokenList.expected("typename"),
						   token);
		return token.getToken();
	}
	
	private static Datatype parseType(TokenList tl) {
		String name = typename(tl);
		List<Datatype> subtypes = new ArrayList<>();
	        if (!name.equals("function")
		    && !name.equals("string")
		    && !name.equals("boolean")
		    && !name.equals("number")
		    && acceptIfNext(tl, "<<")) {
			do {
				Datatype t = parseType(tl);
				subtypes.add(t);
			} while (acceptIfNext(tl, ","));
			accept(tl, ">>");
		}
		return new Datatype(name, subtypes);
	}
	
	static class Program {
		List<Function> functions;
		List<Record> records;
		List<Function> blocks;
		Program(List<Function> functions,
			List<Record> records,
			List<Function> blocks) {
			this.functions = functions;
			this.records = records;
			this.blocks = blocks;
		}
	}
	
	static Program parse(TokenList tl) {
		//System.err.println(tl);
		if (acceptIfNext(tl, "#")) { // purkkaa, tee tämä lekseriin
			while (!nextString(tl).equals("\n"));
		}

		List<Function> functions = new ArrayList<>();
		List<Function> blocks = new ArrayList<>();
		List<Record> records = new ArrayList<>();
		while (!isNext(tl, "<EOF>")) {
			List<Annotation> annotations = parseAnnotations(tl);
			if (isNext(tl, "record")) {
				records.add(parseRecord(tl, annotations));
			}
			else if (isNext(tl, "{")) {
				blocks.add(new Function(
						"<block>",
						Collections.emptyList(),
						Collections.emptyList(), false,
						Collections.emptyList(),
						parseBody(tl)));
			}
			else {
				functions.add(parseFunction(tl, true));
			}
		}
		return new Program(functions, records, blocks);
	}

	public static class Annotation {
		public final String name;
		public final Arguments args;
		final String file;
		final int line;
		public Annotation(String file, int line, String name, Arguments args) {
			this.file = file;
			this.line = line;
			this.name = name;
			this.args = args;
		}
	}

	private static List<Annotation> parseAnnotations(TokenList tl) {
		List<Annotation> annotations = new ArrayList<>();
		while (acceptIfNext(tl, "@")) {
			String file = seek(tl).getFile();
			int line = seek(tl).getLine();
			String name = "@" + identifier(tl);
			Arguments arguments = parseArguments(tl, false);
			if (!arguments.sfvarguments.isEmpty()) {
				throw new ParsingException(
						"annotations can't have underscore arguments",
						tl.seek());
			}
			annotations.add(new Annotation(file, line, name, arguments));
		}
		return annotations;
	}

	public static class Record {
		public static class Field {
			public final String name;
			public final Datatype type;
			final Expression defaultValue;
			public final List<Annotation> annotations;

			public Field(String name,
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
		public final List<String> typeparams, params;
		public final List<Datatype> superTypes;
		public final List<Annotation> annotations;
		public final List<Field> fields;
		public final boolean isValueType;

		public Record(String name,
		       List<String> typeparams,
		       List<Datatype> superTypes,
		       List<Field> fields,
		       boolean isValueType) {
			this(name, typeparams, Collections.emptyList(), superTypes, Collections.emptyList(), fields, isValueType);
		}

		Record(String name,
		       List<String> typeparams,
		       List<String> params,
		       List<Datatype> superTypes,
		       List<Annotation> annotations,
		       List<Field> fields,
		       boolean isValueType) {
			this.name = name;
			this.typeparams = Collections.unmodifiableList(typeparams);
			this.params = Collections.unmodifiableList(params);
			this.annotations = Collections.unmodifiableList(annotations);
			this.fields = Collections.unmodifiableList(fields);
			this.superTypes = superTypes;
			this.isValueType = isValueType;
		}
	}

	static Record parseRecord(TokenList tl, List<Annotation> recordAnnotations) {
		accept(tl, "record");

		boolean isValueType = acceptIfNext(tl, "value");

		String name = identifier(tl);
		List<String> typeparams = parseTypeparameters(tl);

		List<String> params = new ArrayList<>();
		if (acceptIfNext(tl, "(")) {
			params.add(identifier(tl));
			while (acceptIfNext(tl, ","))
				params.add(identifier(tl));
			accept(tl, ")");
		}

		List<Datatype> superTypes = new ArrayList<>();
		if (acceptIfNext(tl, ":")) {
			superTypes.add(parseType(tl));
			while (acceptIfNext(tl, ",")) {
				superTypes.add(parseType(tl));
			}
		}
		
		List<Record.Field> fields = new ArrayList<>();
		
		accept(tl, "{");
		while (!isNext(tl, "}")) {
			List<Annotation> annotations = parseAnnotations(tl);
			if (isNext(tl, "function")) {
				String file = seek(tl).getFile();
				int line = seek(tl).getLine();
				Function method = parseFunction(tl, false);
				String fieldName = method.name;
				Datatype type = new Datatype("function");
				Expression defaultValue = expressionFunction(file, line, method);
				fields.add(new Record.Field(fieldName, type, defaultValue, annotations));
			}
			else {
				String fieldName = identifier(tl);
				accept(tl, ":");
				Datatype type = parseType(tl);
				Expression defaultValue = null;
				if (acceptIfNext(tl, "=")) {
					defaultValue = parseExpression(tl);
				}
				fields.add(new Record.Field(fieldName, type, defaultValue, annotations));
			}
		}
		accept(tl, "}");

		return new Record(name, typeparams, params, superTypes, recordAnnotations, fields, isValueType);
	}

	public static class Function {
		public String name;
		public List<String> typeparams;
		public List<Parameter> parameters, kwparameters;
		public boolean isVarargs;
		public List<Statement> body;

		Function(String name,
			 List<String> typeparams,
			 List<Parameter> parameters,
			 boolean isVarargs,
			 List<Parameter> kwparameters,
			 List<Statement> body) {
			
			for (Parameter p : parameters)
				if (p.defaultValue != null)
					throw new IllegalArgumentException("non-kw parameters can't have default values");
			
			for (Parameter p : kwparameters)
				if (p.defaultValue == null)
					throw new IllegalArgumentException("kw parameters must have default values");

			this.name = name;
			this.typeparams = typeparams;
			this.parameters = parameters;
			this.kwparameters = kwparameters;
			this.isVarargs = isVarargs;
			this.body = body;
		}
	}

	public static class Parameter {
		public String name;
		public boolean reference;
		public Datatype type;
		public Expression defaultValue;
        public Parameter(String name, boolean reference) {
			this(name, reference, null, null);
		}
        public Parameter(String name, boolean reference, Datatype type) {
			this(name, reference, type, null);
		}
        public Parameter(String name, boolean reference, Expression dafaultValue) {
			this(name, reference, null, dafaultValue);
		}
        public Parameter(String name, boolean reference, Datatype type, Expression defaultValue) {
        	if (reference && defaultValue != null)
        		throw new IllegalArgumentException("a reference parameter can't have a default value");
			this.name = name;
			this.reference = reference;
			this.type = type;
			this.defaultValue = defaultValue;
		}
	}

	static Parameter parseParameter(TokenList tl, boolean allowTypes) {
		boolean reference = false;
		if (acceptIfNext(tl, "&")) {
			reference = true;
		}
		String name = varname(tl);
		Datatype type = null;
		if (!reference && allowTypes && isNext(tl, ":")) {
			accept(tl, ":");
			type = parseType(tl);
		}
		Expression expr = null;
		if (!reference && acceptIfNext(tl, "=")) {
			expr = parseExpression(tl);
		}
		return new Parameter(name, reference, type, expr);
	}
	
	static Function parseFunction(TokenList tl, boolean mayBeAnnotation) {
		acceptIfNext(tl, "function");

		boolean isAnnotation = mayBeAnnotation ? acceptIfNext(tl, "@") : false;
		String name = (isAnnotation ? "@" : "") + identifier(tl);
		List<String> typeparams = parseTypeparameters(tl);

		List<Parameter> parameters = new ArrayList<>();
		List<Parameter> kwparameters = new ArrayList<>();
		boolean kwargsMode = false;
		boolean isVarargs = false;
		boolean parentheses = acceptIfNext(tl, "(");
		while (!isNext(tl, ":") && !isNext(tl, "{") && !isNext(tl, ")")) {
			Parameter p = parseParameter(tl, parentheses);
			if (p.defaultValue != null || kwargsMode) {
				kwargsMode = true;
				kwparameters.add(p);
			}
			else if (!kwargsMode) parameters.add(p);
			else accept(tl, "="); // kw-parametrin jälkeen ei voi tulla tavallisia parametreja
			if (!isVarargs && acceptIfNext(tl, "...")) {
				isVarargs = true;
			}
			if (!isNext(tl, ":") && !isNext(tl, "{") && !isNext(tl, ")")) {
				accept(tl, ",");
			}
		}
		if (parentheses) accept(tl, ")");

		List<Statement> body = parseBody(tl);

		return new Function(name, typeparams, parameters, isVarargs, kwparameters, body);
	}

	static List<String> parseTypeparameters(TokenList tl) {
		List<String> typeparams = new ArrayList<>();
		if (acceptIfNext(tl, "<<")) {
			do {
				String typeparam = identifier(tl);
				typeparams.add(typeparam);
			} while (acceptIfNext(tl, ","));
			accept(tl, ">>");
		}
		return typeparams;
	}

	static List<Statement> parseBody(TokenList tl) {
		accept(tl, "{");
		List<Statement> body = new ArrayList<>();
		while (!isNext(tl, "}")) {
			body.add(parseStatement(tl));
		}
		accept(tl, "}");
		return body;
	}

	static class Statement {
		List<Command> commands;
		Statement(List<Command> commands) {
			this.commands = commands;
		}
		
		String asString() {
			return commands.stream().map(Command::asString).collect(joining(" | "));
		}
	}

	static Statement parseStatement(TokenList tl) {
		List<Command> commands = new ArrayList<>();
		commands.add(parseCommand(tl));
		while (acceptIfNext(tl, "|")) {
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
			TRY_DO,
			VARIABLE,
			RETURN,
			BREAK,
			CONTINUE,
			EXPRESSION
		}
		Type type;
		Expression name;
		String operator;
		List<Datatype> typearguments;
		Arguments arguments;
		boolean negation;
		Statement cond;
		String variable;
		List<String> variables;
		Expression list;
		List<Statement> body, elseBody;
		Command cmd;
		Command() {} // käytä apufunktioita alla
		String file;
		int line;
		
		String argumentsAsString() {
			Stream<String> args = arguments.arguments.stream()
					.map(arg -> (arg.flattened ? "*" : "") + arg.expr.asString());
			Stream<String> kwargs = arguments.kwarguments.stream()
					.map(arg -> arg.name + "=" + arg.expr.asString());
			return Stream.concat(args, kwargs).collect(joining(", "));
		}
		
		String asString() {
			switch (type) {
			case NORMAL:
				return name.asString() + "(" + argumentsAsString() + ")";
			case BREAK:
				return "break";
			case CONTINUE:
				return "continue";
			case RETURN:
				return "return " + argumentsAsString();
			case IF:
				return (negation ? "unless " : "if ") + cond.asString()
					+ " do ... done";
			case WHILE:
				return (negation ? "until " : "while ") + cond.asString()
					+ " do ... done";
			case FOR:
				return "for " + variables.stream().collect(joining(", "))
						+ (list != null ? " in " + list.asString() : "") + " do ... done";
			case TRY:
				return "try " + cmd.asString();
			case TRY_DO:
				return "try do ... done";
			case VARIABLE:
				return name.asString() + operator + argumentsAsString();
			default:
				return "<" + type + ">";
			}
		}
	}
	
	static class Arguments {
		List<Argument> arguments;
		List<KwArgument> kwarguments;
		List<String> sfvarguments;
	}

	static class Argument {
		boolean flattened;
		Expression expr;
	}
	
	static class KwArgument {
		String name;
		Expression expr;
	}

	static Argument _makeArgument(boolean flattened, Expression expr) {
		Argument arg = new Argument();
		arg.flattened = flattened;
		arg.expr = expr;
		return arg;
	}

	static KwArgument _makeKwArgument(String name, Expression expr) {
		KwArgument arg = new KwArgument();
		arg.name = name;
		arg.expr = expr;
		return arg;
	}
	
	static Command _makeNormalCommand(String file, int line, Expression name,
					  List<Datatype> typearguments,
					  Arguments arguments) {
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
					    String operator, Arguments arguments) {
		Command cmd = new Command();
		cmd.type = Command.Type.VARIABLE;
		cmd.file = file;
		cmd.line = line;
		cmd.name = name;
		cmd.operator = operator;
		cmd.arguments = arguments;
		return cmd;
	}

	static Command _makeIfOrWhileCommand(String file, int line, boolean isWhile, boolean isNegated,
					     Statement cond, List<Statement> body, List<Statement> elseBody) {
		Command cmd = new Command();
		cmd.type = isWhile ? Command.Type.WHILE : Command.Type.IF;
		cmd.file = file;
		cmd.line = line;
		cmd.negation = isNegated;
		cmd.cond = cond;
		cmd.body = body;
		cmd.elseBody = elseBody;
		return cmd;
	}
	
	static Command _makeForCommand(String file, int line, List<String> variables,
				       Expression list, Statement cond, List<Statement> body) {
		Command cmd = new Command();
		cmd.type = Command.Type.FOR;
		cmd.file = file;
		cmd.line = line;
		cmd.variables = variables;
		cmd.list = list;
		cmd.cond = cond;
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
	
	static Command _makeReturnCommand(String file, int line, Arguments arguments) {
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
	
	static Command _makeExpressionCommand(String file, int line, Expression expr) {
		Command cmd = new Command();
		cmd.type = Command.Type.EXPRESSION;
		cmd.file = file;
		cmd.line = line;
		cmd.name = expr;
		return cmd;
	}

	static Command parseCommand(TokenList tl) {
		Command cmd = parsePrefixCommand(tl);
		return parseSuffix(tl, cmd);
	}
	
	static Command parseSuffix(TokenList tl, Command cmd) {

		if (tl.isNext("while", "if", "unless", "until")) {
			String commandName = nextString(tl);
			boolean isWhile = commandName.equals("while") || commandName.equals("until");
			boolean isUnless = commandName.equals("unless") || commandName.equals("until");
			Statement cond = parseStatement(tl);
			return _makeIfOrWhileCommand(cmd.file, cmd.line, isWhile, isUnless, cond,
						     Arrays.asList(new Statement(Arrays.asList(cmd))), null);
		}
		else if (tl.acceptIfNext("for")) {
			List<String> variables = new ArrayList<>();
			variables.add(varname(tl));
			while (acceptIfNext(tl, ",")) {
				variables.add(varname(tl));
			}
			Expression list = null;
			Statement cond = null;
			if (acceptIfNext(tl, "in")) {
				list = parseExpression(tl);
			}
			if (acceptIfNext(tl, "if")) {
				cond = parseStatement(tl);
			}
			return _makeForCommand(cmd.file, cmd.line, variables, list, cond,
					       Arrays.asList(new Statement(Arrays.asList(cmd))));
		}
		else return cmd;
	}
	
	static Command parsePrefixCommand(TokenList tl) {
		String file = seek(tl).getFile();
		int line = seek(tl).getLine();
		if (isNext(tl, "while", "until", "if", "unless")) {
			String commandName = nextString(tl);
			boolean isWhile = commandName.equals("while") || commandName.equals("until");
			boolean isUnless = commandName.equals("unless") || commandName.equals("until");
			Statement cond = parseStatement(tl);
			accept(tl, "do");
			List<Statement> body = new ArrayList<>(), elseBody = null;
			while (!isNext(tl, "done") && !isNext(tl, "else")) {
				body.add(parseStatement(tl));
				if (isNext(tl, "done")) break;
			}
			if (isNext(tl, "else")) {
				accept(tl, "else");
				elseBody = new ArrayList<>();
				while (!isNext(tl, "done")) {
					elseBody.add(parseStatement(tl));
					if (isNext(tl, "done")) break;
				}
			}
			accept(tl, "done");
			return _makeIfOrWhileCommand(file, line, isWhile, isUnless, cond, body, elseBody);
		}

		if (acceptIfNext(tl, "for")) {
			List<String> variables = new ArrayList<>();
			variables.add(varname(tl));
			while (acceptIfNext(tl, ",")) {
				variables.add(varname(tl));
			}
			Expression list = null;
			Statement cond = null;
			if (acceptIfNext(tl, "in")) {
				list = parseExpression(tl);
			}
			if (acceptIfNext(tl, "if")) {
				cond = parseStatement(tl);
			}
			accept(tl, "do");
			List<Statement> body = new ArrayList<>();
			while (!isNext(tl, "done")) {
				body.add(parseStatement(tl));
				if (isNext(tl, "done")) break;
			}
			accept(tl, "done");
			return _makeForCommand(file, line, variables, list, cond, body);
		}

		if (acceptIfNext(tl, "try")) {
			if (isNext(tl, "do")) {
				accept(tl, "do");
				List<Statement> body = new ArrayList<>();
				while (!isNext(tl, "catch", "done")) {
					body.add(parseStatement(tl));
					if (isNext(tl, "done")) break;
				}
				String catchVar = null;
				List<Statement> elseBody = null;
				if (acceptIfNext(tl, "catch")) {
					catchVar = varname(tl);
					elseBody = new ArrayList<>();
					while (!isNext(tl, "done")) {
						elseBody.add(parseStatement(tl));
						if (isNext(tl, "done")) break;
					}
				}
				accept(tl, "done");
				return _makeTryCommand(file, line, body, catchVar, elseBody);
			} else {
				return _makeTryCommand(file, line, parseCommand(tl));
			}
		}

		if (acceptIfNext(tl, "return")) {
			Arguments arguments = parseArguments(tl, false);
			return _makeReturnCommand(file, line, arguments);
		}

		if (acceptIfNext(tl, "break")) {
			return _makeBreakCommand(file, line);
		}

		if (acceptIfNext(tl, "continue")) {
			return _makeContinueCommand(file, line);
		}

		Expression name = parseExpressionPrimary(tl, false);
		String operator = null;
		List<Datatype> typeargs = new ArrayList<>();
		if (isNext(tl, ":=", "=", "++", "--", "+=", "-=", "*=", "/=", ".=", "~=", "?")) {
			operator = nextString(tl);
		}
		else if (acceptIfNext(tl, "<<")) {
			do {
				typeargs.add(parseType(tl));
			} while (acceptIfNext(tl, ","));
			accept(tl, ">>");
		}
		Arguments arguments;
		
		if (acceptIfNext(tl, "(")) {
			arguments = parseArguments(tl, true);
			accept(tl, ")");
		}
		else arguments = parseArguments(tl, false);
		
		Command cmd;
		
		if (operator == null)
			cmd = _makeNormalCommand(file, line, name, typeargs, arguments);
		else
			cmd = _makeVariableCommand(file, line, name, operator, arguments);
		
		if (!arguments.sfvarguments.isEmpty()) {
			cmd = _makeForCommand(
					cmd.file,
					cmd.line,
					arguments.sfvarguments,
					null, null,
					Arrays.asList(new Statement(Arrays.asList(cmd))));
		}
		
		return cmd;
	}
	
	private static int SUGAR_FOR_VARNUM_COUNTER = 1;

	private static Arguments parseArguments(TokenList tl, boolean allowNewlines) {
		Arguments arguments = new Arguments();
		arguments.arguments = new ArrayList<>();
		arguments.kwarguments = new ArrayList<>();
		arguments.sfvarguments = new ArrayList<>();
		boolean kwargMode = false;
		while ((allowNewlines || !tl.isNext(";", "\n")) && !isNext(tl, "|", ")", "]", "}", "in", "do", "done", "<EOF>")) {
			if (seekString(tl).equals("_")) {
				String sfvname = "<sfv" + (SUGAR_FOR_VARNUM_COUNTER++) + ">";
				arguments.arguments.add(_makeArgument(false,
						expressionVariable(
								tl.seek().getFile(),
								tl.seek().getLine(),
								sfvname)));
				accept(tl, "_");
				arguments.sfvarguments.add(sfvname);
			}
			else if (tl.seekString(1).equals("=") || kwargMode) { // TODO: ei salli rivinvaihtoa nimen ja =-merkin väliin
				kwargMode = true;
				String name = identifier(tl);
				accept(tl, "=");
				arguments.kwarguments.add(_makeKwArgument(name, parseExpression(tl)));
			}
			else {
				boolean flattened = acceptIfNext(tl, "*");
				arguments.arguments.add(_makeArgument(flattened,
							    parseExpression(tl)));
			}
			if (!acceptIfNext(tl, ",")) break;
			else skipNewlines(tl);
		}
		return arguments;
	}
	
	public static class Expression {
		enum Type {
			VARIABLE,
			STRING,
			FLAG,
			INTEGER,
			FLOATING,
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
			TYPEOF,
			IS,
			IN
		}
		public enum CType {
			MUL,
			DIV,
			IDIV,
			MOD,
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
		int integer;
		double floating;
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
			case FLAG:
				return string;
			case INTEGER:
				return String.valueOf(integer);
			case SLICE: {
				String i1 = index1 == null ? "" : index1.asString();
				String i2 = index2 == null ? "" : index2.asString();
				return sub.asString() + "[" + i1 + ":" + i2 + "]";
			}
			case BLOCK:
				return "{...}";
			case LIST:
				return "[...]";
			case STATEMENT_LIST:
				return "[" + statement.asString() + "]";
			case STATEMENT_SINGLE:
				return statement.asString();
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
				return "new " + datatype.toString() + "(" + list.stream().map(Expression::asString).collect(joining(", ")) + ")";
			case REFLECT:
				return "reflect " + datatype.toString();
			case TYPEOF:
				return "typeof " + sub.asString();
			case IS:
				return sub.asString() + " is " + datatype.toString();
			case IN:
				return exprA.asString() + " in " + exprB.asString();
			case CALCULATOR:
				return "<" + ctype.toString() + " "
					+ (isUnary ? sub.asString() : exprA.asString() + ", " + exprB.asString())
					+ ">";
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

	public static Expression expressionString(String file, int line, String t) {
		Expression e = new Expression();
		e.type = Expression.Type.STRING;
		e.file = file;
		e.line = line;
		e.string = t;
		return e;
	}

	private static Expression expressionFlag(String file, int line, String t) {
		Expression e = new Expression();
		e.type = Expression.Type.FLAG;
		e.file = file;
		e.line = line;
		e.string = t;
		return e;
	}

	public static Expression expressionInt(String file, int line, int d) {
		Expression e = new Expression();
		e.type = Expression.Type.INTEGER;
		e.file = file;
		e.line = line;
		e.integer = d;
		return e;
	}

	public static Expression expressionFloat(String file, int line, double d) {
		Expression e = new Expression();
		e.type = Expression.Type.FLOATING;
		e.file = file;
		e.line = line;
		e.floating = d;
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

	private static Expression expressionNew(String file, int line, Datatype datatype, List<Expression> args) {
		Expression e = new Expression();
		e.type = Expression.Type.NEW;
		e.file = file;
		e.line = line;
		e.datatype = datatype;
		e.list = args;
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

	private static Expression expressionIs(String file, int line, Expression sub, Datatype datatype) {
		Expression e = new Expression();
		e.type = Expression.Type.IS;
		e.file = file;
		e.line = line;
		e.sub = sub;
		e.datatype = datatype;
		return e;
	}

	private static Expression expressionIn(String file, int line, Expression a, Expression b) {
		Expression e = new Expression();
		e.type = Expression.Type.IN;
		e.file = file;
		e.line = line;
		e.exprA = a;
		e.exprB = b;
		return e;
	}

	private static Expression parseArrayAccessIfPossible(TokenList tl, Expression ans, boolean allowCalls) {
		while (isNext(tl, "[", ".", "is") || allowCalls && isNext(tl, "(", "<<")) {
			String file = seek(tl).getFile();
			int line = seek(tl).getLine();
			if (acceptIfNext(tl, "[")) {
				Expression e1 = isNext(tl, ":") ? null : parseExpression(tl);
				if (isNext(tl, ":")) {
					accept(tl, ":");
					Expression e2 = isNext(tl, "]") ? null : parseExpression(tl);
					accept(tl, "]");
					ans = expressionSlice(file, line, ans, e1, e2);
				}
				else {
					accept(tl, "]");
					if (acceptIfNext(tl, "?")) {
						ans = expressionContains(file, line, ans, e1);
					}
					else {
						ans = expressionElement(file, line, ans, e1);
					}
				}
			}
			else if (allowCalls && isNext(tl, "<<", "(")) {
				List<Datatype> typeargs = new ArrayList<>();
				if (acceptIfNext(tl, "<<")) {
					do {
						typeargs.add(parseType(tl));
					} while (acceptIfNext(tl, ","));
					accept(tl, ">>");
				}
				accept(tl, "(");
				List<Command> commands = new ArrayList<>();
				Arguments args = parseArguments(tl, true);
				if (!args.sfvarguments.isEmpty()) {
					throw new ParsingException("the first command in the pipeline in an expression "
							+ "can't have underscore arguments", tl.seek());
				}
				Command cmd = _makeNormalCommand(file, line, ans, typeargs, args);
				accept(tl, ")");
				commands.add(parseSuffix(tl, cmd));
				while (acceptIfNext(tl, "|")) {
					commands.add(parseCommand(tl));
				}
				ans = expressionStatementSingle(file,  line, new Statement(commands));
			}
			else if (acceptIfNext(tl, ".")) {
				String field = identifier(tl);
				ans = expressionField(file, line, ans, field);
			}
			else if (acceptIfNext(tl, "is")) {
				Datatype dt = parseType(tl);
				ans = expressionIs(file, line, ans, dt);
			}
			else assert false;
		}
		return ans;
	}
	
	private static OperatorLibrary<Expression> library;
	private static OperatorPrecedenceParser<Expression> opparser;

	private static BinaryOperator<Expression> op(Expression.CType type) {
		return (a, b) -> expressionCalculator(a.file, a.line, type, a, b);
	}
	
	static {
		/* Declares the operator library – all operators use parsePrimary() as their RHS parser */
		library = new OperatorLibrary<>(tl -> parseExpressionPrimary(tl, true));
		
		/* Declares the operators */
		library.add("and", op(Expression.CType.AND));
		library.add("or", op(Expression.CType.OR));
		library.add("xor", op(Expression.CType.XOR));
		library.increaseLevel();
		library.add("=", op(Expression.CType.EQ));
		library.add("=~", op(Expression.CType.MATCHES));
		library.add("!=", op(Expression.CType.NEQ));
		library.increaseLevel();
		library.add("..", (a, b) -> expressionConcat(a.file, a.line, a, b));
		library.increaseLevel();
		library.add("&", (a, b) -> expressionJoin(a.file, a.line, a, b));
		library.increaseLevel();
		library.add("<", op(Expression.CType.LT));
		library.add(">", op(Expression.CType.GT));
		library.add("<=", op(Expression.CType.LE));
		library.add(">=", op(Expression.CType.GE));
		library.add("in", (a, b) -> expressionIn(a.file, a.line, a, b));
		library.increaseLevel();
		library.add("b_and", op(Expression.CType.BAND));
		library.add("b_or", op(Expression.CType.BOR));
		library.add("b_xor", op(Expression.CType.BXOR));
		library.add("b_shiftl", op(Expression.CType.BLSHIFT));
		library.add("b_shiftr", op(Expression.CType.BRSHIFT));
		library.add("b_shiftrr", op(Expression.CType.BRRSHIFT));
		library.increaseLevel();
		library.add("+", op(Expression.CType.ADD));
		library.add("-", op(Expression.CType.SUB));
		library.increaseLevel();
		library.add("*", op(Expression.CType.MUL));
		library.add("/", op(Expression.CType.DIV));
		library.add("//", op(Expression.CType.IDIV));
		library.add("%", op(Expression.CType.MOD));
		
		/* Declares the OPP */
		opparser = OperatorPrecedenceParser.fromLibrary(library);
	}
	
	private static Expression parseExpression(TokenList tl) {
		Expression e = opparser.parse(tl);
		return e;
	}

	private static Expression parseExpressionPrimary(TokenList tl, boolean allowCalls) {
		String file = seek(tl).getFile();
		int line = seek(tl).getLine();
		Expression ans = null;
		if (acceptIfNext(tl, "new")) {
			Datatype type = parseType(tl);
			List<Expression> args = new ArrayList<>();
			if (acceptIfNext(tl, "(")) {
				if (!isNext(tl, ")")) {
					args.add(parseExpression(tl));
					while (acceptIfNext(tl, ","))
						args.add(parseExpression(tl));
				}
				accept(tl, ")");
			}
			ans = expressionNew(file, line, type, args);
		}
		else if (acceptIfNext(tl, "reflect")) {
			Datatype type = parseType(tl);
			ans = expressionReflect(file, line, type);
		}
		else if (acceptIfNext(tl, "typeof")) {
			Expression e = parseExpressionPrimary(tl, true);
			return expressionTypeof(file, line, e);
		}
		else if (acceptIfNext(tl, "#")) {
			Expression e = parseExpressionPrimary(tl, true);
			return expressionLength(file, line, e);
		}
		else if (isNext(tl, "{")) {
			List<Parameter> parameters = new ArrayList<>();
			List<Parameter> kwparameters = new ArrayList<>();
			boolean isVarargs = false, kwargsMode = false;
			accept(tl, "{");
			if (isNext(tl, "|")) {
				accept(tl, "|");
				while (!isNext(tl, "|")) {
					Parameter p = parseParameter(tl, true);
					if (p.defaultValue != null || kwargsMode) {
						kwargsMode = true;
						kwparameters.add(p);
					}
					else if (!kwargsMode) parameters.add(p);
					else accept(tl, "="); // kw-parametrin jälkeen ei voi tulla tavallisia parametreja
					if (isNext(tl, "...") && !kwargsMode) {
						accept(tl, "...");
						isVarargs = true;
						break;
					}
					else if (!isNext(tl, "|")) accept(tl, ",");
				}
				accept(tl, "|");
			}
			List<Statement> body = new ArrayList<>();
			while (!isNext(tl, "}")) {
				body.add(parseStatement(tl));
			}
			accept(tl, "}");
			ans = expressionFunction(file, line,
					new Function("<block>",
							Collections.emptyList(),
							parameters, isVarargs,
							kwparameters, body));
		}
		else if (acceptIfNext(tl, "\"")) {
			String s = tl.nextString();
			tl.accept("\"");
		    ans = expressionString(file, line, s);
		}
		else if (seekString(tl).matches("[0-9]+")) {
			ans = expressionInt(file, line, Integer.parseInt(nextString(tl)));
		}
		else if (seekString(tl).matches(NUMBER_REGEX)) {
			ans = expressionFloat(file, line, Double.parseDouble(nextString(tl)));
		}
		else if (acceptIfNext(tl, "[")) {
			List<Expression> list = new ArrayList<>();
			while (!isNext(tl, "]")) {
				list.add(parseExpression(tl));
				if (!isNext(tl, "]")) accept(tl, ",");
			}
			accept(tl, "]");
			if (list.size() == 1 && list.get(0).type == Expression.Type.STATEMENT_SINGLE) {
				ans = expressionStatementList(file, line, list.get(0).statement);
			}
			else ans = expressionList(file, line, list);
		}
		else if (acceptIfNext(tl, "-")) {
			return expressionCalculatorUnary(file, line, Expression.CType.NEG,
							 parseExpressionPrimary(tl, true));
		}
		else if (acceptIfNext(tl, "b_not")) {
			return expressionCalculatorUnary(file, line, Expression.CType.BNOT,
							 parseExpressionPrimary(tl, true));
		}
		else if (acceptIfNext(tl, "not")) {
			return expressionCalculatorUnary(file, line, Expression.CType.NOT,
							 parseExpressionPrimary(tl, true));
		}
		else if (acceptIfNext(tl, "(")) {
			Expression e = parseExpression(tl);
			accept(tl, ")");
			ans = e;
		}
		else if (isNext(tl, "if", "while", "unless", "until", "for", "try")) {
			Statement s = parseStatement(tl);
			ans = expressionStatementSingle(file, line, s);
		}
		else if (acceptIfNext(tl, ":")) {
			String flag = nextString(tl);
			ans = expressionFlag(file, line, "-"+flag);
		}
		else {
			String name = identifier(tl);
			ans = expressionVariable(file, line, name);
		}

		ans = parseArrayAccessIfPossible(tl, ans, allowCalls);

		return ans;
	}
}
