package org.kaivos.röda;

import static java.util.stream.Collectors.joining;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.kaivos.nept.parser.OperatorLibrary;
import org.kaivos.nept.parser.OperatorPrecedenceParser;
import org.kaivos.nept.parser.ParsingException;
import org.kaivos.nept.parser.Token;
import org.kaivos.nept.parser.TokenList;
import org.kaivos.nept.parser.TokenScanner;

public class Parser {

	private static final String NUMBER_REGEX = "(0|[1-9][0-9]*)(\\.[0-9]+)([eE](\\+|-)?[0-9]+)?";
	private static final Pattern NUMBER_PATTERN = Pattern.compile("^"+NUMBER_REGEX);
	private static final Pattern NUMBER_PATTERN_ALL = Pattern.compile("^"+NUMBER_REGEX+"$");
	private static final Pattern INT_PATTERN_ALL = Pattern.compile("^[0-9]+$");
	private static final Pattern SUGAR_PATTERN_ALL = Pattern.compile("^_([1-9][0-9]*)?$");
	
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
		.addOperatorRule("//=")
		.addOperatorRule("%=")
		.addOperatorRule("^=")
		.addOperatorRule("++")
		.addOperatorRule("--")
		.addOperatorRule("//")
		.addOperatorRule("!=")
		.addOperatorRule("=~")
		.addOperatorRule("!~")
		.addOperatorRule("<=")
		.addOperatorRule(">=")
		.addOperatorRule("<<")
		.addOperatorRule(">>")
		.addOperatorRule("<>")
		.addPatternRule(NUMBER_PATTERN, '0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
		.addOperators("<>()[]{}|&.,:;=#%!?\n\\+-*/^~@%$")
		.separateIdentifiersAndPunctuation(false)
		.addCommentRule("/*", "*/")
		.addCommentRule("#!", "\n")
		.addStringRule('"','"','\\')
		.addStringRule('`','`','\0')
		.addEscapeCode('\\', "\\")
		.addEscapeCode('n', "\n")
		.addEscapeCode('r', "\r")
		.addEscapeCode('t', "\t")
		.ignore("\\\n")
		.dontIgnore('\n')
		.addCharacterEscapeCode('x', 2, 16)
		.appendOnEOF("<EOF>");

	public static String operatorCharacters = "<>()[]{}|&.,:;=#%!?\n\\+-*/^~@%$\"`";
	private static String notIdentifierStart = operatorCharacters + "_";
	
	private static Token seek(TokenList tl) {
		int i = 1;
		while (tl.has(i)) {
			if (!tl.seekString(i-1).equals("\n") && !tl.seekString(i-1).equals(";")) return tl.seek(i-1);
			i++;
		}
		throw new ParsingException("Unexpected EOF", tl.seek(i-2));
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
	
	public static boolean validIdentifier(String applicant) {
		if (!validTypename(applicant)) return false;
		switch (applicant) {
		case "function":
		case "list":
		case "map":
		case "string":
		case "number":
		case "integer":
		case "floating":
		case "boolean":
		case "namespace":
			return false;
		default:
			return true;
		}
	}

	private static boolean validTypename(String applicant) {
		if (applicant.length() == 0) return false;
		if (applicant.length() >= 1 && notIdentifierStart.indexOf(applicant.charAt(0)) >= 0) return false;
		switch (applicant) {
		/* avainsanat */
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
		case "try":
		case "catch":
		case "del":
		/* operaattorit*/
		case ":=":
		case "~=":
		case ".=":
		case "+=":
		case "-=":
		case "*=":
		case "/=":
		case "!=":
		case "++":
		case "--":
		case "//":
		case "=~":
		case "!~":
		case "<=":
		case ">=":
		case "<<":
		case ">>":
		case "<>":
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
	
	public static class DatatypeTree {
		public final List<String> name;
		public final List<DatatypeTree> subtypes;

		public DatatypeTree(List<String> name,
				List<DatatypeTree> subtypes) {
			this.name = Collections.unmodifiableList(name);
			this.subtypes = Collections.unmodifiableList(subtypes);
		}

		public DatatypeTree(String name) {
			this.name = Arrays.asList(name);
			this.subtypes = Collections.emptyList();
		}

		@Override
		public String toString() {
			if (subtypes.isEmpty())
				return name.stream().collect(joining("."));
			return name + "<" + subtypes.stream()
				.map(DatatypeTree::toString)
				.collect(joining(", ")) + ">";
		}
	}
	
	private static List<String> typenameChain(TokenList tl) {
		List<String> chain = new ArrayList<>();
		chain.add(typename(tl));
		while (acceptIfNext(tl, ".")) chain.add(typename(tl));
		return chain;
	}
	
	private static DatatypeTree parseType(TokenList tl) {
		List<String> name = typenameChain(tl);
		List<DatatypeTree> subtypes = new ArrayList<>();
	        if (!name.equals("function")
		    && !name.equals("string")
		    && !name.equals("boolean")
		    && !name.equals("number")
		    && acceptIfNext(tl, "<<")) {
			do {
				DatatypeTree t = parseType(tl);
				subtypes.add(t);
			} while (acceptIfNext(tl, ","));
			accept(tl, ">>");
		}
		return new DatatypeTree(name, subtypes);
	}
	
	static class ProgramTree {
		List<FunctionTree> functions;
		List<RecordTree> records;
		List<List<StatementTree>> preBlocks, postBlocks;
		ProgramTree(List<FunctionTree> functions,
			List<RecordTree> records,
			List<List<StatementTree>> preBlocks,
			List<List<StatementTree>> postBlocks) {
			this.functions = functions;
			this.records = records;
			this.preBlocks = preBlocks;
			this.postBlocks = postBlocks;
		}
	}
	
	static ProgramTree parse(TokenList tl) {
		//System.err.println(tl);
		if (acceptIfNext(tl, "#")) { // purkkaa, tee tämä lekseriin
			while (!nextString(tl).equals("\n"));
		}

		List<FunctionTree> functions = new ArrayList<>();
		List<List<StatementTree>> preBlocks = new ArrayList<>(), postBlocks = new ArrayList<>();
		List<RecordTree> records = new ArrayList<>();
		while (!isNext(tl, "<EOF>")) {
			skipNewlines(tl);
			List<AnnotationTree> annotations = parseAnnotations(tl);
			if (isNext(tl, "record")) {
				records.add(parseRecord(tl, annotations));
			}
			else if (isNext(tl, "{")) {
				preBlocks.add(parseBody(tl));
			}
			/* TODO: ei salli uusiarivejä väliin */
			else if (validIdentifier(tl.seekString()) && tl.seekString(1).equals(":")) {
				String eventName = nextString(tl);
				accept(tl, ":");
				List<StatementTree> block = parseBody(tl);
				if (eventName.equals("pre_load")) {
					preBlocks.add(block);
				}
				else if (eventName.equals("post_load")) {
					postBlocks.add(block);
				}
			}
			else {
				functions.add(parseFunction(tl, true));
			}
		}
		return new ProgramTree(functions, records, preBlocks, postBlocks);
	}

	public static class AnnotationTree {
		public final String name;
		public final List<String> namespace;
		public final ArgumentsTree args;
		final String file;
		final int line;
		public AnnotationTree(String file, int line,
				String name, List<String> namespace, ArgumentsTree args) {
			this.file = file;
			this.line = line;
			this.name = name;
			this.namespace = Collections.unmodifiableList(namespace);
			this.args = args;
		}
	}

	private static List<AnnotationTree> parseAnnotations(TokenList tl) {
		List<AnnotationTree> annotations = new ArrayList<>();
		while (acceptIfNext(tl, "@")) {
			String file = seek(tl).getFile();
			int line = seek(tl).getLine();
			List<String> namespace = new ArrayList<>();
			while (tl.seekString(1).equals(".")) {
				namespace.add(identifier(tl));
				accept(tl, ".");
				skipNewlines(tl);
			}
			String name = "@" + identifier(tl);
			ArgumentsTree arguments;
			if (acceptIfNext(tl, "(")) {
				arguments = parseArguments(tl, true);
				accept(tl, ")");
			} else arguments = parseArguments(tl, false, true);
			annotations.add(new AnnotationTree(file, line, name, namespace, arguments));
		}
		return annotations;
	}

	public static class RecordTree {
		public static class FieldTree {
			public final String name;
			public final DatatypeTree type;
			final ExpressionTree defaultValue;
			public final List<AnnotationTree> annotations;

			public FieldTree(String name,
			      DatatypeTree type) {
				this(name, type, null, Collections.emptyList());
			}

			FieldTree(String name,
			      DatatypeTree type,
			      ExpressionTree defaultValue,
			      List<AnnotationTree> annotations) {
				this.name = name;
				this.type = type;
				this.defaultValue = defaultValue;
				this.annotations = Collections.unmodifiableList(annotations);
			}
		}
		public static class SuperExpression {
			public final DatatypeTree type;
			final List<ExpressionTree> args;
			
			SuperExpression(DatatypeTree type, List<ExpressionTree> args) {
				this.type = type;
				this.args = args;
			}
		}

		public final String name;
		public final List<String> typeparams, params;
		public final List<SuperExpression> superTypes;
		public final List<AnnotationTree> annotations;
		public final List<FieldTree> fields;
		public final boolean isValueType;

		public RecordTree(String name,
		       List<String> typeparams,
		       List<SuperExpression> superTypes,
		       List<FieldTree> fields,
		       boolean isValueType) {
			this(name, typeparams, Collections.emptyList(), superTypes, Collections.emptyList(), fields, isValueType);
		}

		RecordTree(String name,
		       List<String> typeparams,
		       List<String> params,
		       List<SuperExpression> superTypes,
		       List<AnnotationTree> annotations,
		       List<FieldTree> fields,
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

	static RecordTree parseRecord(TokenList tl, List<AnnotationTree> recordAnnotations) {
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

		List<RecordTree.SuperExpression> superTypes = new ArrayList<>();
		if (acceptIfNext(tl, ":")) {
			do {
				DatatypeTree type = parseType(tl);
				List<ExpressionTree> args = new ArrayList<>();
				if (acceptIfNext(tl, "(")) {
					do {
						args.add(parseExpression(tl));
					} while (acceptIfNext(tl, ","));
					accept(tl, ")");
				}
				superTypes.add(new RecordTree.SuperExpression(type, args));
			} while (acceptIfNext(tl, ","));
		}
		
		List<RecordTree.FieldTree> fields = new ArrayList<>();
		
		accept(tl, "{");
		while (!isNext(tl, "}")) {
			List<AnnotationTree> annotations = parseAnnotations(tl);
			if (isNext(tl, "function")) {
				String file = seek(tl).getFile();
				int line = seek(tl).getLine();
				FunctionTree method = parseFunction(tl, false);
				String fieldName = method.name;
				DatatypeTree type = new DatatypeTree("function");
				ExpressionTree defaultValue = expressionFunction(file, line, method);
				fields.add(new RecordTree.FieldTree(fieldName, type, defaultValue, annotations));
			}
			else {
				String fieldName = identifier(tl);
				accept(tl, ":");
				DatatypeTree type = parseType(tl);
				ExpressionTree defaultValue = null;
				if (acceptIfNext(tl, "=")) {
					defaultValue = parseExpression(tl);
				}
				fields.add(new RecordTree.FieldTree(fieldName, type, defaultValue, annotations));
			}
		}
		accept(tl, "}");

		return new RecordTree(name, typeparams, params, superTypes, recordAnnotations, fields, isValueType);
	}

	public static class FunctionTree {
		public String name;
		public List<String> typeparams;
		public List<ParameterTree> parameters, kwparameters;
		public boolean isVarargs;
		public List<StatementTree> body;

		FunctionTree(String name,
			 List<String> typeparams,
			 List<ParameterTree> parameters,
			 boolean isVarargs,
			 List<ParameterTree> kwparameters,
			 List<StatementTree> body) {
			
			for (ParameterTree p : parameters)
				if (p.defaultValue != null)
					throw new IllegalArgumentException("non-kw parameters can't have default values");
			
			for (ParameterTree p : kwparameters)
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

	public static class ParameterTree {
		public String name;
		public boolean reference;
		public DatatypeTree type;
		public ExpressionTree defaultValue;
        public ParameterTree(String name, boolean reference) {
			this(name, reference, null, null);
		}
        public ParameterTree(String name, boolean reference, DatatypeTree type) {
			this(name, reference, type, null);
		}
        public ParameterTree(String name, boolean reference, ExpressionTree dafaultValue) {
			this(name, reference, null, dafaultValue);
		}
        public ParameterTree(String name, boolean reference, DatatypeTree type, ExpressionTree defaultValue) {
        	if (reference && defaultValue != null)
        		throw new IllegalArgumentException("a reference parameter can't have a default value");
			this.name = name;
			this.reference = reference;
			this.type = type;
			this.defaultValue = defaultValue;
		}
	}

	static ParameterTree parseParameter(TokenList tl, boolean allowTypes) {
		boolean reference = false;
		if (acceptIfNext(tl, "&")) {
			reference = true;
		}
		String name = varname(tl);
		DatatypeTree type = null;
		if (!reference && allowTypes && isNext(tl, ":")) {
			accept(tl, ":");
			type = parseType(tl);
		}
		ExpressionTree expr = null;
		if (!reference && acceptIfNext(tl, "=")) {
			expr = parseExpression(tl);
		}
		return new ParameterTree(name, reference, type, expr);
	}
	
	static FunctionTree parseFunction(TokenList tl, boolean mayBeAnnotation) {
		acceptIfNext(tl, "function");

		boolean isAnnotation = mayBeAnnotation ? acceptIfNext(tl, "@") : false;
		String name = (isAnnotation ? "@" : "") + identifier(tl);
		List<String> typeparams = parseTypeparameters(tl);

		List<ParameterTree> parameters = new ArrayList<>();
		List<ParameterTree> kwparameters = new ArrayList<>();
		boolean kwargsMode = false;
		boolean isVarargs = false;
		boolean parentheses = acceptIfNext(tl, "(");
		while (!isNext(tl, ":") && !isNext(tl, "{") && !isNext(tl, ")")) {
			ParameterTree p = parseParameter(tl, parentheses);
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

		List<StatementTree> body = parseBody(tl);

		return new FunctionTree(name, typeparams, parameters, isVarargs, kwparameters, body);
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

	static List<StatementTree> parseBody(TokenList tl) {
		accept(tl, "{");
		List<StatementTree> body = new ArrayList<>();
		while (!isNext(tl, "}")) {
			body.add(parseStatement(tl));
		}
		accept(tl, "}");
		return body;
	}

	public static class StatementTree {
		List<Command> commands;
		StatementTree(List<Command> commands) {
			this.commands = commands;
		}
		
		String asString() {
			return commands.stream().map(Command::asString).collect(joining(" | "));
		}
	}
	
	static StatementTree parseStatement(TokenList tl) {
		return parseStatement(tl, true);
	}

	static StatementTree parseStatement(TokenList tl, boolean allowSugarVars) {
		List<Command> commands = new ArrayList<>();
		commands.add(parseCommand(tl, allowSugarVars));
		while (acceptIfNext(tl, "|")) {
			commands.add(parseCommand(tl));
		}
		return new StatementTree(commands);
	}

	static class Command {
		enum Type {
			NORMAL,
			INTERLEAVE,
			WHILE,
			IF,
			FOR,
			TRY,
			TRY_DO,
			VARIABLE,
			RETURN,
			BREAK,
			CONTINUE,
			EXPRESSION,
			DEL
		}
		Type type;
		ExpressionTree name;
		String operator;
		List<DatatypeTree> typearguments;
		ArgumentsTree arguments;
		boolean negation;
		StatementTree cond;
		String variable;
		List<String> variables;
		ExpressionTree list;
		List<StatementTree> body, elseBody;
		List<Command> cmds;
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
			case INTERLEAVE:
				return cmds.stream().map(Command::asString).collect(joining(" <> "));
			case BREAK:
				return "break";
			case CONTINUE:
				return "continue";
			case RETURN:
				return "return " + argumentsAsString();
			case IF:
				return (negation ? "unless " : "if ") + cond.asString()
					+ " do " + (body.size() == 1 ? body.get(0).asString() : "...")
					+ (elseBody != null ? " else " + (elseBody.size() == 1 ? elseBody.get(0).asString() : "...") : "")
					+ " done";
			case WHILE:
				return (negation ? "until " : "while ") + cond.asString()
					+ " do " + (body.size() == 1 ? body.get(0).asString() : "...")
					+ (elseBody != null ? " else " + (elseBody.size() == 1 ? elseBody.get(0).asString() : "...") : "")
					+ " done";
			case FOR:
				return "for " + variables.stream().collect(joining(", "))
						+ (list != null ? " in " + list.asString() : "")
						+ " do "
						+ (body.size() == 1 ? body.get(0).asString() : "...")
						+ " done";
			case TRY:
				return "try " + cmd.asString();
			case TRY_DO:
				if (body.size() == 1)
					return "try " + body.get(0).asString();
				else
					return "try do ... done";
			case VARIABLE:
				return name.asString() + operator + argumentsAsString();
			case DEL:
				return "del " + name.asString();
			default:
				return "<" + type + ">";
			}
		}
	}
	
	static class ArgumentsTree {
		List<ArgumentTree> arguments;
		List<KwArgumentTree> kwarguments;
	}

	static class ArgumentTree {
		boolean flattened;
		ExpressionTree expr;
	}
	
	static class KwArgumentTree {
		String name;
		ExpressionTree expr;
	}

	static ArgumentTree _makeArgument(boolean flattened, ExpressionTree expr) {
		ArgumentTree arg = new ArgumentTree();
		arg.flattened = flattened;
		arg.expr = expr;
		return arg;
	}

	static KwArgumentTree _makeKwArgument(String name, ExpressionTree expr) {
		KwArgumentTree arg = new KwArgumentTree();
		arg.name = name;
		arg.expr = expr;
		return arg;
	}
	
	static Command _makeNormalCommand(String file, int line, ExpressionTree name,
					  List<DatatypeTree> typearguments,
					  ArgumentsTree arguments) {
		Command cmd = new Command();
		cmd.type = Command.Type.NORMAL;
		cmd.file = file;
		cmd.line = line;
		cmd.name = name;
		cmd.typearguments = typearguments;
		cmd.arguments = arguments;
		return cmd;
	}
	
	static Command _makeInterleaveCommand(String file, int line, List<Command> cmds) {
		Command cmd = new Command();
		cmd.type = Command.Type.INTERLEAVE;
		cmd.file = file;
		cmd.line = line;
		cmd.cmds = cmds;
		return cmd;
	}
	
	static Command _makeVariableCommand(String file, int line, ExpressionTree name,
					    String operator, ArgumentsTree arguments) {
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
					     StatementTree cond, List<StatementTree> body, List<StatementTree> elseBody) {
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
				       ExpressionTree list, StatementTree cond, List<StatementTree> body) {
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

	static Command _makeTryCommand(String file, int line, List<StatementTree> body,
				       String catchVar, List<StatementTree> elseBody) {
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
	
	static Command _makeReturnCommand(String file, int line, ArgumentsTree arguments) {
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
	
	static Command _makeExpressionCommand(String file, int line, ExpressionTree expr) {
		Command cmd = new Command();
		cmd.type = Command.Type.EXPRESSION;
		cmd.file = file;
		cmd.line = line;
		cmd.name = expr;
		return cmd;
	}
	
	static Command _makeDelCommand(String file, int line, ExpressionTree expr) {
		Command cmd = new Command();
		cmd.type = Command.Type.DEL;
		cmd.file = file;
		cmd.line = line;
		cmd.name = expr;
		return cmd;
	}
	
	static Command parseCommand(TokenList tl) {
		return parseCommand(tl, true);
	}

	static Command parseCommand(TokenList tl, boolean allowSugarVars) {
		if (allowSugarVars) sugarVars.push(new ArrayList<>());
		
		Command cmd = parsePrefixCommand(tl);
		cmd = parseSuffix(tl, cmd);
		
		if (allowSugarVars) {
			List<String> sfvs = sugarVars.pop();
			
			if (!sfvs.isEmpty()) {
				cmd = _makeForCommand(
						cmd.file,
						cmd.line,
						sfvs,
						null, null,
						Arrays.asList(new StatementTree(Arrays.asList(cmd))));
			}
		}
		return cmd;
	}
	
	static Command parseSuffix(TokenList tl, Command cmd) {

		if (tl.isNext("while", "if", "unless", "until")) {
			String commandName = nextString(tl);
			boolean isWhile = commandName.equals("while") || commandName.equals("until");
			boolean isUnless = commandName.equals("unless") || commandName.equals("until");
			StatementTree cond = parseStatement(tl, false);
			List<StatementTree> elseBody = null;
			if (tl.acceptIfNext("else")) {
				elseBody = Arrays.asList(new StatementTree(Arrays.asList(parseCommand(tl, false))));
			}
			return _makeIfOrWhileCommand(cmd.file, cmd.line, isWhile, isUnless, cond,
						     Arrays.asList(new StatementTree(Arrays.asList(cmd))), elseBody);
		}
		else if (tl.acceptIfNext("for")) {
			List<String> variables = new ArrayList<>();
			variables.add(varname(tl));
			while (acceptIfNext(tl, ",")) {
				variables.add(varname(tl));
			}
			ExpressionTree list = null;
			StatementTree cond = null;
			if (acceptIfNext(tl, "in")) {
				list = parseExpression(tl);
			}
			if (acceptIfNext(tl, "if")) {
				cond = parseStatement(tl, false);
			}
			return _makeForCommand(cmd.file, cmd.line, variables, list, cond,
					       Arrays.asList(new StatementTree(Arrays.asList(cmd))));
		}
		else if (tl.acceptIfNext("<>")) {
			List<Command> cmds = new ArrayList<>();
			cmds.add(cmd);
			cmds.add(parsePrefixCommand(tl));
			while (tl.acceptIfNext("<>")) {
				cmds.add(parsePrefixCommand(tl));
			}
			Command ans = _makeInterleaveCommand(cmd.file, cmd.line, cmds);
			return parseSuffix(tl, ans);
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
			StatementTree cond = parseStatement(tl, false);
			accept(tl, "do");
			List<StatementTree> body = new ArrayList<>(), elseBody = null;
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
			ExpressionTree list = null;
			StatementTree cond = null;
			if (acceptIfNext(tl, "in")) {
				list = parseExpression(tl);
			}
			if (acceptIfNext(tl, "if")) {
				cond = parseStatement(tl, false);
			}
			accept(tl, "do");
			List<StatementTree> body = new ArrayList<>();
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
				List<StatementTree> body = new ArrayList<>();
				while (!isNext(tl, "catch", "done")) {
					body.add(parseStatement(tl));
					if (isNext(tl, "done")) break;
				}
				String catchVar = null;
				List<StatementTree> elseBody = null;
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
			ArgumentsTree arguments = parseArguments(tl, false);
			return _makeReturnCommand(file, line, arguments);
		}

		if (acceptIfNext(tl, "break")) {
			return _makeBreakCommand(file, line);
		}

		if (acceptIfNext(tl, "continue")) {
			return _makeContinueCommand(file, line);
		}

		if (acceptIfNext(tl, "del")) {
			return _makeDelCommand(file, line, parseExpressionPrimary(tl, false));
		}

		ExpressionTree name = parseExpressionPrimary(tl, false);
		String operator = null;
		List<DatatypeTree> typeargs = new ArrayList<>();
		if (isNext(tl, ":=", "=", "++", "--", "+=", "-=", "*=", "/=", "//=", "%=", "^=", ".=", "~=", "?")) {
			operator = nextString(tl);
		}
		else if (acceptIfNext(tl, "<<")) {
			do {
				typeargs.add(parseType(tl));
			} while (acceptIfNext(tl, ","));
			accept(tl, ">>");
		}
		ArgumentsTree arguments;
		
		if (tl.acceptIfNext("(")) { // ei saa olla uuttariviä ennen (-merkkiä
			arguments = parseArguments(tl, true);
			accept(tl, ")");
		}
		else arguments = parseArguments(tl, false, operator != null && !operator.equals("++") && !operator.equals("--"));
		//                                                             ^ purkkaa, ++:lle ja --:lle ei pitäisi parsia
		//                                                               argumentteja ollenkaan
		
		Command cmd;
		
		if (operator == null)
			cmd = _makeNormalCommand(file, line, name, typeargs, arguments);
		else
			cmd = _makeVariableCommand(file, line, name, operator, arguments);
		
		return cmd;
	}
	
	private static int SUGAR_FOR_VARNUM_COUNTER = 1;
	
	private static Deque<List<String>> sugarVars = new ArrayDeque<>();
	
	private static String sugarVar(String sugar) {
		if (sugar.equals("_")) {
			String sfvname = "<sfv" + (SUGAR_FOR_VARNUM_COUNTER++) + ">";
			sugarVars.peek().add(sfvname);
			return sfvname;
		} else {
			int position = Integer.parseInt(sugar.substring(1)) - 1;
			List<String> sfvList = sugarVars.peek();
			while (sfvList.size() <= position) {
				sfvList.add("<sfv" + (SUGAR_FOR_VARNUM_COUNTER++) + ">");
			}
			return sfvList.get(position);
		}
	}
	
	private static ArgumentsTree parseArguments(TokenList tl, boolean allowNewlines) {
		return parseArguments(tl, allowNewlines, allowNewlines);
	}

	private static ArgumentsTree parseArguments(TokenList tl, boolean allowNewlines, boolean allowPrefixes) {
		ArgumentsTree arguments = new ArgumentsTree();
		arguments.arguments = new ArrayList<>();
		arguments.kwarguments = new ArrayList<>();
		boolean kwargMode = false;
		while ((allowNewlines || !tl.isNext(";", "\n"))
				&& (allowPrefixes || !tl.isNext("for", "while", "until", "if", "unless"))
				&& !isNext(tl, "|", ")", "]", "}", "in", "do", "else", "done", "<>", "<EOF>")) {
			if (validIdentifier(tl.seekString()) && tl.seekString(1).equals("=") || kwargMode) { // TODO: ei salli rivinvaihtoa nimen ja =-merkin väliin
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
	
	public static class ExpressionTree {
		enum Type {
			VARIABLE,
			STRING,
			PATTERN,
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
			CONCAT_CHILDREN,
			JOIN,
			CALCULATOR,
			NEW,
			REFLECT,
			TYPEOF,
			IS,
			IN
		}
		public enum CType {
			POW("^"),
			MUL("*"),
			DIV("/"),
			IDIV("//"),
			MOD("%"),
			ADD("+"),
			SUB("-"),
			BAND("b_and"),
			BOR("b_or"),
			BXOR("b_xor"),
			BRSHIFT("b_shiftr"),
			BRRSHIFT("b_shiftrr"),
			BLSHIFT("b_shiftl"),
			EQ("="),
			NEQ("!="),
			MATCHES("~="),
			NO_MATCH("!="),
			LT("<"),
			GT(">"),
			LE("<="),
			GE(">="),
			AND("and"),
			OR("or"),
			XOR("xor"),
			
			NEG("-", true),
			BNOT("b_not", true),
			NOT("not", true);
			
			String op;
			boolean unary;
			
			private CType(String op, boolean unary) {
				this.op = op;
				this.unary = unary;
			}
			private CType(String op) { this(op, false); }
		}
		Type type;
		CType ctype;
		boolean isUnary;
		String variable;
		String string;
		Pattern pattern;
		long integer;
		double floating;
		StatementTree statement;
		FunctionTree block;
		List<ExpressionTree> list;
		ExpressionTree sub, index, index1, index2, step, exprA, exprB;
		String field;
		DatatypeTree datatype;

		String file;
		int line;

		String asString() {
			switch (type) {
			case VARIABLE:
				return variable;
			case STRING:
				return "\"" + string.replaceAll("\\\\", "\\\\\\\\").replaceAll("\"", "\\\\\"") + "\"";
			case PATTERN:
				return "r:\"" + pattern.pattern().replaceAll("\\\\", "\\\\\\\\").replaceAll("\"", "\\\\\"") + "\"";
			case INTEGER:
				return String.valueOf(integer);
			case SLICE: {
				String i1 = index1 == null ? "" : index1.asString();
				String i2 = index2 == null ? "" : index2.asString();
				String s = step == null ? "" : ":" + step.asString();
				return sub.asString() + "[" + i1 + ":" + i2 + s + "]";
			}
			case BLOCK:
				return "{...}";
			case LIST:
				if (list.size() == 1)
					return "[" + list.get(0).asString() + "]";
				else
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
				return "new " + datatype.toString() + "(" + list.stream().map(ExpressionTree::asString).collect(joining(", ")) + ")";
			case REFLECT:
				return "reflect " + datatype.toString();
			case TYPEOF:
				return "typeof " + sub.asString();
			case IS:
				return sub.asString() + " is " + datatype.toString();
			case IN:
				return exprA.asString() + " in " + exprB.asString();
			case CALCULATOR:
				return isUnary
						? ctype.op + " " + sub.asString()
						: exprA.asString() + " " + ctype.op + " " + exprB.asString();
			default:
				return "<" + type + ">";
			}
		}
	}

	private static ExpressionTree expressionVariable(String file, int line, String t) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.VARIABLE;
		e.file = file;
		e.line = line;
		e.variable = t;
		return e;
	}

	public static ExpressionTree expressionString(String file, int line, String t) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.STRING;
		e.file = file;
		e.line = line;
		e.string = t;
		return e;
	}

	public static ExpressionTree expressionPattern(String file, int line, String t) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.PATTERN;
		e.file = file;
		e.line = line;
		e.pattern = Pattern.compile(t);
		return e;
	}

	public static ExpressionTree expressionInt(String file, int line, long d) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.INTEGER;
		e.file = file;
		e.line = line;
		e.integer = d;
		return e;
	}

	public static ExpressionTree expressionFloat(String file, int line, double d) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.FLOATING;
		e.file = file;
		e.line = line;
		e.floating = d;
		return e;
	}

	private static ExpressionTree expressionStatementList(String file, int line, StatementTree statement) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.STATEMENT_LIST;
		e.file = file;
		e.line = line;
		e.statement = statement;
		return e;
	}

	private static ExpressionTree expressionStatementSingle(String file, int line, StatementTree statement) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.STATEMENT_SINGLE;
		e.file = file;
		e.line = line;
		e.statement = statement;
		return e;
	}

	private static ExpressionTree expressionFunction(String file, int line, FunctionTree block) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.BLOCK;
		e.file = file;
		e.line = line;
		e.block = block;
		return e;
	}

	private static ExpressionTree expressionList(String file, int line, List<ExpressionTree> list) { 
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.LIST;
		e.file = file;
		e.line = line;
		e.list = list;
		return e;
	}

	private static ExpressionTree expressionLength(String file, int line, ExpressionTree sub) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.LENGTH;
		e.file = file;
		e.line = line;
		e.sub = sub;
		return e;
	}

	private static ExpressionTree expressionElement(String file, int line, ExpressionTree list, ExpressionTree index) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.ELEMENT;
		e.file = file;
		e.line = line;
		e.sub = list;
		e.index = index;
		return e;
	}

	private static ExpressionTree expressionContains(String file, int line, ExpressionTree list, ExpressionTree index) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.CONTAINS;
		e.file = file;
		e.line = line;
		e.sub = list;
		e.index = index;
		return e;
	}

	private static ExpressionTree expressionField(String file, int line, ExpressionTree list, String field) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.FIELD;
		e.file = file;
		e.line = line;
		e.sub = list;
		e.field = field;
		return e;
	}

	private static ExpressionTree expressionSlice(String file, int line,
						  ExpressionTree list, ExpressionTree index1, ExpressionTree index2, ExpressionTree step) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.SLICE;
		e.file = file;
		e.line = line;
		e.sub = list;
		e.index1 = index1;
		e.index2 = index2;
		e.step = step;
		return e;
	}

	private static ExpressionTree expressionConcat(String file, int line, ExpressionTree a, ExpressionTree b) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.CONCAT;
		e.file = file;
		e.line = line;
		e.exprA = a;
		e.exprB = b;
		return e;
	}

	private static ExpressionTree expressionConcatChildren(String file, int line, ExpressionTree a, ExpressionTree b) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.CONCAT_CHILDREN;
		e.file = file;
		e.line = line;
		e.exprA = a;
		e.exprB = b;
		return e;
	}

	private static ExpressionTree expressionJoin(String file, int line, ExpressionTree a, ExpressionTree b) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.JOIN;
		e.file = file;
		e.line = line;
		e.exprA = a;
		e.exprB = b;
		return e;
	}

	private static ExpressionTree expressionCalculator(String file, int line, ExpressionTree.CType type,
						       ExpressionTree a, ExpressionTree b) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.CALCULATOR;
		e.ctype = type;
		e.isUnary = false;
		e.file = file;
		e.line = line;
		e.exprA = a;
		e.exprB = b;
		return e;
	}

	private static ExpressionTree expressionCalculatorUnary(String file, int line, ExpressionTree.CType type,
							    ExpressionTree sub) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.CALCULATOR;
		e.ctype = type;
		e.isUnary = true;
		e.file = file;
		e.line = line;
		e.sub = sub;
		return e;
	}

	public static ExpressionTree expressionNew(String file, int line, DatatypeTree datatype, List<ExpressionTree> args) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.NEW;
		e.file = file;
		e.line = line;
		e.datatype = datatype;
		e.list = args;
		return e;
	}

	private static ExpressionTree expressionReflect(String file, int line, DatatypeTree datatype) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.REFLECT;
		e.file = file;
		e.line = line;
		e.datatype = datatype;
		return e;
	}

	private static ExpressionTree expressionTypeof(String file, int line, ExpressionTree sub) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.TYPEOF;
		e.file = file;
		e.line = line;
		e.sub = sub;
		return e;
	}

	private static ExpressionTree expressionIs(String file, int line, ExpressionTree sub, DatatypeTree datatype) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.IS;
		e.file = file;
		e.line = line;
		e.sub = sub;
		e.datatype = datatype;
		return e;
	}

	private static ExpressionTree expressionIn(String file, int line, ExpressionTree a, ExpressionTree b) {
		ExpressionTree e = new ExpressionTree();
		e.type = ExpressionTree.Type.IN;
		e.file = file;
		e.line = line;
		e.exprA = a;
		e.exprB = b;
		return e;
	}

	private static ExpressionTree parseArrayAccessIfPossible(TokenList tl, ExpressionTree ans, boolean allowCalls) {
		while (isNext(tl, ".", "is", "&") || tl.isNext("[") || allowCalls && (isNext(tl, "<<") || tl.isNext("("))) {
			String file = seek(tl).getFile();
			int line = seek(tl).getLine();
			if (acceptIfNext(tl, "[")) {
				ExpressionTree e1 = isNext(tl, ":") ? null : parseExpression(tl);
				if (isNext(tl, ":")) {
					accept(tl, ":");
					ExpressionTree e2 = isNext(tl, ":", "]") ? null : parseExpression(tl);
					ExpressionTree e3 = null;
					if (acceptIfNext(tl, ":")) {
						e3 = parseExpression(tl);
					}
					accept(tl, "]");
					ans = expressionSlice(file, line, ans, e1, e2, e3);
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
				List<DatatypeTree> typeargs = new ArrayList<>();
				if (acceptIfNext(tl, "<<")) {
					do {
						typeargs.add(parseType(tl));
					} while (acceptIfNext(tl, ","));
					accept(tl, ">>");
				}
				accept(tl, "(");
				ArgumentsTree args = parseArguments(tl, true);
				Command cmd = _makeNormalCommand(file, line, ans, typeargs, args);
				accept(tl, ")");
				List<Command> commands = new ArrayList<>();
				commands.add(parseSuffix(tl, cmd));
				while (acceptIfNext(tl, "|")) {
					commands.add(parseCommand(tl));
				}
				ans = expressionStatementSingle(file,  line, new StatementTree(commands));
			}
			else if (acceptIfNext(tl, ".")) {
				String field = identifier(tl);
				ans = expressionField(file, line, ans, field);
			}
			else if (acceptIfNext(tl, "is")) {
				DatatypeTree dt = parseType(tl);
				ans = expressionIs(file, line, ans, dt);
			}
			else if (acceptIfNext(tl, "&")) {
				ExpressionTree et = parseExpressionPrimary(tl, true);
				ans = expressionJoin(file, line, ans, et);
			}
			else assert false;
		}
		return ans;
	}
	
	private static OperatorLibrary<ExpressionTree> library;
	private static OperatorPrecedenceParser<ExpressionTree> opparser;

	private static BinaryOperator<ExpressionTree> op(ExpressionTree.CType type) {
		return (a, b) -> expressionCalculator(a.file, a.line, type, a, b);
	}
	
	static {
		/* Declares the operator library – all operators use parsePrimary() as their RHS parser */
		library = new OperatorLibrary<>(tl -> parseExpressionPrimary(tl, true));
		
		/* Declares the operators */
		library.add("and", op(ExpressionTree.CType.AND));
		library.add("or", op(ExpressionTree.CType.OR));
		library.add("xor", op(ExpressionTree.CType.XOR));
		library.increaseLevel();
		library.add("=", op(ExpressionTree.CType.EQ));
		library.add("!=", op(ExpressionTree.CType.NEQ));
		library.add("=~", op(ExpressionTree.CType.MATCHES));
		library.add("!~", op(ExpressionTree.CType.NO_MATCH));
		library.increaseLevel();
		library.add("..", (a, b) -> expressionConcat(a.file, a.line, a, b));
		library.add("...", (a, b) -> expressionConcatChildren(a.file, a.line, a, b));
		library.increaseLevel();
		library.add("<", op(ExpressionTree.CType.LT));
		library.add(">", op(ExpressionTree.CType.GT));
		library.add("<=", op(ExpressionTree.CType.LE));
		library.add(">=", op(ExpressionTree.CType.GE));
		library.add("in", (a, b) -> expressionIn(a.file, a.line, a, b));
		library.increaseLevel();
		library.add("b_and", op(ExpressionTree.CType.BAND));
		library.add("b_or", op(ExpressionTree.CType.BOR));
		library.add("b_xor", op(ExpressionTree.CType.BXOR));
		library.add("b_shiftl", op(ExpressionTree.CType.BLSHIFT));
		library.add("b_shiftr", op(ExpressionTree.CType.BRSHIFT));
		library.add("b_shiftrr", op(ExpressionTree.CType.BRRSHIFT));
		library.increaseLevel();
		library.add("+", op(ExpressionTree.CType.ADD));
		library.add("-", op(ExpressionTree.CType.SUB));
		library.increaseLevel();
		library.add("*", op(ExpressionTree.CType.MUL));
		library.add("/", op(ExpressionTree.CType.DIV));
		library.add("//", op(ExpressionTree.CType.IDIV));
		library.add("%", op(ExpressionTree.CType.MOD));
		library.increaseLevel();
		library.add("^", op(ExpressionTree.CType.POW));
		
		/* Declares the OPP */
		opparser = OperatorPrecedenceParser.fromLibrary(library);
	}
	
	private static ExpressionTree parseExpression(TokenList tl) {
		ExpressionTree e = opparser.parse(tl);
		return e;
	}

	private static ExpressionTree parseExpressionPrimary(TokenList tl, boolean allowCalls) {
		skipNewlines(tl);
		String file = seek(tl).getFile();
		int line = seek(tl).getLine();
		ExpressionTree ans = null;
		if (acceptIfNext(tl, "new")) {
			DatatypeTree type = parseType(tl);
			List<ExpressionTree> args = new ArrayList<>();
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
			DatatypeTree type = parseType(tl);
			ans = expressionReflect(file, line, type);
		}
		else if (acceptIfNext(tl, "typeof")) {
			ExpressionTree e = parseExpressionPrimary(tl, true);
			return expressionTypeof(file, line, e);
		}
		else if (acceptIfNext(tl, "#")) {
			ExpressionTree e = parseExpressionPrimary(tl, true);
			return expressionLength(file, line, e);
		}
		else if (isNext(tl, "{")) {
			List<ParameterTree> parameters = new ArrayList<>();
			List<ParameterTree> kwparameters = new ArrayList<>();
			boolean isVarargs = false, kwargsMode = false;
			accept(tl, "{");
			if (isNext(tl, "|")) {
				accept(tl, "|");
				while (!isNext(tl, "|")) {
					ParameterTree p = parseParameter(tl, true);
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
			List<StatementTree> body = new ArrayList<>();
			while (!isNext(tl, "}")) {
				body.add(parseStatement(tl));
			}
			accept(tl, "}");
			ans = expressionFunction(file, line,
					new FunctionTree("<block at " + file + ":" + line + ">",
							Collections.emptyList(),
							parameters, isVarargs,
							kwparameters, body));
		}
		else if (acceptIfNext(tl, "\"")) {
			String s = tl.nextString();
			tl.accept("\"");
		    ans = expressionString(file, line, s);
		}
		else if (acceptIfNext(tl, "`")) { /* mallineliteraali */
			Token t = tl.next();
			String s = t.getToken();
			String[] fragments = s.split("\\$");
		    ans = expressionString(file, line, fragments[0]);
		    for (int i = 1; i < fragments.length; i++) {
		    	int stringStart = fragments[i].length();
		    	if (stringStart == 0) throw new ParsingException("No variable name after $.", t);
		    	/* aaltosulut merkitsevät, että upotus sisältää kokonaisen lausekkeen */
		    	boolean expression = fragments[i].charAt(0) == '{';
		    	int depth = 0;
		    	for (int j = 0; j < fragments[i].length(); j++) {
		    		char c = fragments[i].charAt(j);
		    		/* pidetään kirjaa aaltosuluista ja katkaistaan lauseke uloimpaan sulkevaan aaltosulkuun */
		    		if (expression && c == '{') depth++;
		    		else if (expression && c == '}') {
		    			depth--;
		    			if (depth == 0) {
			    			stringStart = j+1;
			    			break;
		    			}
		    		}
		    		/* katkaistaan muuttujanimi ensimmäiseen merkkiin, joka ei voi olla osa muuttujanimeä */
		    		else if (!expression && (Character.isWhitespace(c) || operatorCharacters.indexOf(c) >= 0)) {
		    			stringStart = j;
		    			break;
		    		}
		    	}
		    	String var = fragments[i].substring(0, stringStart);
		    	ExpressionTree varExp;
		    	if (expression) {
		    		var = var.substring(1, var.length()-1); // poistetaan aaltosulut
		    		TokenList tl2 = Parser.t.tokenize(var, file, line);
		    		varExp = parseExpression(tl2); // parsitaan lauseke
		    		tl2.accept("<EOF>"); // varmistetaan, että lauseke on loppunut
		    	}
		    	else {
			    	if (!sugarVars.isEmpty() && SUGAR_PATTERN_ALL.matcher(var).matches()) {
			    		var = sugarVar(var);
			    	}
			    	else if (!validIdentifier(var))
						throw new ParsingException(TokenList.expected("identifier"), t);
					varExp = expressionVariable(file, line, var);
		    	}
				ans = expressionConcat(file, line, ans, varExp);
		    	if (stringStart < fragments[i].length()) {
		    		ExpressionTree strExp = expressionString(file, line, fragments[i].substring(stringStart));
		    		ans = expressionConcat(file, line, ans, strExp);
		    	}
		    }
			tl.accept("`");
		}
		else if (acceptIfNext(tl, "[")) {
			List<ExpressionTree> list = new ArrayList<>();
			while (!isNext(tl, "]")) {
				list.add(parseExpression(tl));
				if (!isNext(tl, "]")) accept(tl, ",");
			}
			accept(tl, "]");
			if (list.size() == 1 && list.get(0).type == ExpressionTree.Type.STATEMENT_SINGLE) {
				ans = expressionStatementList(file, line, list.get(0).statement);
			}
			else ans = expressionList(file, line, list);
		}
		else if (acceptIfNext(tl, "-")) {
			return expressionCalculatorUnary(file, line, ExpressionTree.CType.NEG,
							 parseExpressionPrimary(tl, true));
		}
		else if (acceptIfNext(tl, "b_not")) {
			return expressionCalculatorUnary(file, line, ExpressionTree.CType.BNOT,
							 parseExpressionPrimary(tl, true));
		}
		else if (acceptIfNext(tl, "not")) {
			return expressionCalculatorUnary(file, line, ExpressionTree.CType.NOT,
							 parseExpressionPrimary(tl, true));
		}
		else if (acceptIfNext(tl, "(")) {
			ExpressionTree e = parseExpression(tl);
			accept(tl, ")");
			ans = e;
		}
		else if (isNext(tl, "if", "while", "unless", "until", "for", "try")) {
			StatementTree s = parseStatement(tl);
			ans = expressionStatementSingle(file, line, s);
		}
		else if (acceptIfNext(tl, "@")) {
			String name = "@" + identifier(tl);
			ans = expressionVariable(file, line, name);
		}
		else if (INT_PATTERN_ALL.matcher(seekString(tl)).matches()) {
			ans = expressionInt(file, line, Long.parseLong(nextString(tl)));
		}
		else if (NUMBER_PATTERN_ALL.matcher(seekString(tl)).matches()) {
			ans = expressionFloat(file, line, Double.parseDouble(nextString(tl)));
		}
		else if (tl.isNext("r") && tl.seekString(1).equals(":") && tl.seekString(2).equals("\"")) {
			/* r:n ja kaksoispisteen välissä ei saa olla uuttariviä */
			tl.accept("r");
			tl.accept(":");
			tl.accept("\"");
			String s = tl.nextString();
			tl.accept("\"");
		    ans = expressionPattern(file, line, s);
		}
		else if (!sugarVars.isEmpty() && SUGAR_PATTERN_ALL.matcher(seekString(tl)).matches()) {
			String name = nextString(tl);
			String sfvname = sugarVar(name);
			ans = expressionVariable(
							tl.seek().getFile(),
							tl.seek().getLine(),
							sfvname);
		}
		else {
			String name = identifier(tl);
			ans = expressionVariable(file, line, name);
		}

		ans = parseArrayAccessIfPossible(tl, ans, allowCalls);

		return ans;
	}
}
