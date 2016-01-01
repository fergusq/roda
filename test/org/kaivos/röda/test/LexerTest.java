package org.kaivos.röda.test;

import static java.util.stream.Collectors.joining;

import org.junit.*;
import static org.junit.Assert.*;

import org.kaivos.röda.Parser;
import org.kaivos.nept.parser.TokenList;
import org.kaivos.nept.parser.ParsingException;

public class LexerTest {

	String lex(String code) {
		TokenList tl = Parser.t.tokenize(code, "<test>");
		return joinTokens(tl);
	}

	String lexc(String code) {
		TokenList tl = Parser.calculatorScanner.tokenize(code, "<test>");
		return joinTokens(tl);
	}

	String joinTokens(TokenList tl) {
		return tl.toList().stream()
			.map(token -> token.toString().replaceAll(",", ",,"))
			.collect(joining(", "));
	}
	
	@Test
	public void testSimpleProgram() {
		assertEquals("main, {, }, <EOF>", lex("main{}"));
	}

	@Test
	public void testEmptyProgram() {
		assertEquals("<EOF>", lex(""));
	}

	@Test
	public void testEmptyProgramWithSpaces() {
		assertEquals("<EOF>", lex("  \t \t"));
	}

	@Test
	public void testEmptyProgramWithNewlines() {
		assertEquals("\n, \n, <EOF>", lex("  \n \n"));
	}

	@Test
	public void testOperators() {
		assertEquals("<, ->, ), ;, .., [, %, ., (, >, }, #, =, ], {, |, \n, ..., :, \", , \", &, <EOF>",
			     lex("<->);..[%.(>}#=]{|\n...:\"\"&"));
	}

	@Test
	public void testOperatorsAndText() {
		assertEquals("_, <, a, ->, b, ), c, ;, d, .., e, [, f, %, g, ., h, (, i, >, j, "
			     + "}, k, #, l, =, m, ], n, {, o, |, p, \n, q, ..., r, :, s, \", , \", t, &, u, <EOF>",
			     lex("_<a->b)c;d..e[f%g.h(i>j}k#l=m]n{o|p\nq...r:s\"\"t&u"));
	}

	@Test
	public void testDots() {
		assertEquals("..., ..., .., ..., .., ..., ., ., <EOF>",
			     lex("...... .. ..... .... ."));
	}

	@Test
	public void testLinesAndAngles() {
		assertEquals("--, <, >, --, ->, <, --, <, >, >, >, <, ->, --, <EOF>",
			     lex("--<>---><--<>>><->--"));
	}

	@Test
	public void testLinesAndText() {
		assertEquals("a-b, --d, -e, f--gh-ij-, k-, <EOF>",
			     lex("a-b --d -e f--gh-ij- k-"));
	}

	@Test
	public void testStrings() {
		assertEquals("\", abba, \", <EOF>",
			     lex("\"abba\""));
	}

	@Test
	public void testStringsAndEscapeCodes() {
		assertEquals("\", abb\na\"\u00e8t, \", <EOF>",
			     lex("\"abb\\na\\\"\\xe8t\""));
	}

	@Test(expected=ParsingException.class)
	public void testUnclosedString() {
		lex("\"abba");
	}

	@Test(expected=ParsingException.class)
	public void testUnclosedStringWithEscapeCodeAtEnd() {
		lex("\"abba\\\"");
	}
	
	@Test
	public void testCalculatorQuote() {
		assertEquals("', a+(c+d)*e, ', <EOF>",
			     lex("'a+(c+d)*e'"));
	}
	
	@Test
	public void testCalculatorOperators() {
		assertEquals("+, *, /, -, =, =, ], :, #, !=, [, <EOF>",
			     lexc("+*/-==]:#!=["));
	}
	
	@Test
	public void testCalculatorOperatorsAndParentheses() {
		assertEquals("+, *, /, -, (, =, =, ], :, #, ), [, <EOF>",
			     lexc("+*/-(==]:#)["));
	}
	
	@Test
	public void testCalculatorOperatorsAndText() {
		assertEquals("a, *, (, b, +, c, ), -, d, <EOF>",
			     lexc("a*(b+c)-d"));
	}
	
	@Test
	public void testCalculatorOperatorsAndNumbers() {
		assertEquals("1, +, (, 3, +, 1, ), *, 3, <EOF>",
			     lexc("1+(3+1)*3"));
	}
}
