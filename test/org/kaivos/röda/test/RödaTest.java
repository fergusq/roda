package org.kaivos.röda.test;

import org.junit.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;

import static java.util.stream.Collectors.joining;

import org.kaivos.röda.Interpreter;
import static org.kaivos.röda.Interpreter.RödaException;
import org.kaivos.röda.RödaStream;
import static org.kaivos.röda.RödaStream.*;
import org.kaivos.röda.RödaValue;

public class RödaTest {
	private Interpreter interpreter;
	private ArrayList<RödaValue> results;
	
	@Before
	public void init() {
		results = new ArrayList<>();
		RödaStream in = makeStream(v -> {}, () -> null, () -> {}, () -> true);
		RödaStream out = makeStream(v -> results.add(v), () -> null, () -> {}, () -> true);
		if (interpreter != null) cleanup();
		interpreter = new Interpreter(in, out);
	}

	@After
	public void cleanup() {
		interpreter = null;
	}

	private String eval(String code) {
		interpreter.interpret(code, "<test>");
		return getResults();
	}

	private String getResults() {
		return results.stream().map(v -> v.str()).collect(joining(","));
	}

	// Merkkijonoliteraali

	@Test
	public void testNormalLiteral() {
		assertEquals("abba", eval("main{push\"abba\"}"));
	}

	@Test
	public void testLuaLiteral() {
		assertEquals("emma", eval("main{push[[emma]]}"));
	}
	
	// Laskutoimitukset

	@Test
	public void test2Plus2Equals4() {
		assertEquals("4", eval("main{push'2+2';}"));
	}

	@Test
	public void testGrouping() {
		assertEquals("13", eval("main{push'1+(3+1)*3';}"));
	}

	@Test
	public void testVariablesInCalculation() {
		assertEquals("13", eval("main{a:=6;b:=1;b=2;push'1+a*b';}"));
	}

	/* README:n esimerkit (hieman muutettuina)*/

	// Funktiot

	@Test
	public void testSimplePullPushFunction() {
		assertEquals("auto,auto",
			     eval("duplicate{pull a;push a;push a;}main{push\"auto\"|duplicate;}"));
	}

	@Test
	public void testWhilePullPushFunction() {
		assertEquals("auto,auto,talo,talo",
			     eval("duplicate{while pull -r a;do push a;push a;done}"
				  + "main{push\"auto\"\"talo\"|duplicate;}"));
	}

	@Test
	public void testVarargsFunction() {
		assertEquals("got abba,got tuuli,got joki",
			     eval("give words...{for word in words;do push\"got \"..word;done}"
				  + "main{give\"abba\"\"tuuli\"\"joki\"}"));
	}

	// Viittaukset

	@Test
	public void testPullingReference() {
		assertEquals("(abba musiikki)",
			     eval("pull_twice &var{pull a1;pull a2;var=(a1 a2)}"
				  + "main{push\"abba\"\"musiikki\"|pull_twice v;push v}"));
	}

	@Test
	public void testPullingReferenceInAnonymousFunction() {
		assertEquals("17", eval("main{push 17|{|&a|;pull a}b;push b}"));
	}

	@Test
	public void testSettingReference() {
		assertEquals("20", eval("main{push 17|{|&a|;a:=20}b;push b}"));
	}

	// Muuttujat

	@Test
	public void testVariableFlagCreate() {
		assertEquals("tieto.txt", eval("main{tiedosto:=\"tieto.txt\";push tiedosto}"));
		init();
		assertEquals("73", eval("main{ikä:=73;push ikä}"));
		init();
		assertEquals("(Annamari Reetta Vilma)",
			     eval("main{tytöt:=(\"Annamari\" \"Reetta\" \"Vilma\");push tytöt}"));
	}

	@Test
	public void testVariableFlagSet() {
		assertEquals("74", eval("main{ikä:=73;ikä=74;push ikä}"));
	}

	@Test
	public void testVariableFlagAdd() {
		assertEquals("(Annamari Reetta Vilma Maija)",
			     eval("main{tytöt:=(\"Annamari\" \"Reetta\" \"Vilma\");"
				  + "tytöt+=\"Maija\";push tytöt}"));
	}

	@Test
	public void testVariableFlagPut() {
		assertEquals("(Annamari Liisa Vilma)",
			     eval("main{tytöt:=(\"Annamari\" \"Reetta\" \"Vilma\");"
				  + "tytöt[1]=\"Liisa\";push tytöt}"));
	}

	@Test
	public void testVariableFlagIncDec() {
		assertEquals("72,11",
			     eval("main{ikä:=73;voimat:=10;ikä--;voimat++;push ikä voimat}"));
	}

	@Test(expected=RödaException.class)
	public void testPushingStringVariable() {
		assertEquals("abba",
			     eval("main{v:=\"abba\";v}"));
	}

	@Test
	public void testPushingListVariable() {
		assertEquals("joki,virta",
			     eval("main{l:=(\"joki\" \"virta\");l}"));
	}

	@Test
	public void testPushingList() {
		assertEquals("rivi1\n,rivi2\n,rivi3\n",
			     eval("main{(\"rivi1\\n\" \"rivi2\\n\" \"rivi3\\n\")}"));
	}

	// Ohjausrakenteet

	@Test
	public void testIf() {
		assertEquals("Olet liian nuori!\n",
			     eval("main{ikä:=16;if test ikä -lt 18;do push\"Olet liian nuori!\\n\";done}"));
	}

	@Test
	public void testIfWithFalseCondition() {
		assertEquals("",
			     eval("main{ikä:=24;if test ikä -lt 18;do push\"Olet liian nuori!\\n\";done}"));
	}

	@Test
	public void testIfElse() {
		assertEquals("Tervetuloa!\n",
			     eval("main{ikä:=24;if test ikä -ge 18;do push\"Tervetuloa!\\n\";"
				  + "else push\"Olet liian nuori!\\n\";done}"));
	}

	@Test
	public void testIfElseWithFalseCondition() {
		assertEquals("Olet liian nuori!\n",
			     eval("main{ikä:=16;if test ikä -ge 18;do push\"Tervetuloa!\\n\";"
				  + "else push\"Olet liian nuori!\\n\";done}"));
	}

	@Test
	public void testFor() {
		assertEquals("nimi Annamari; syntynyt 1996,nimi Reetta; syntynyt 1992,nimi Vilma; syntynyt 1999",
			     eval("main{tytöt:=((\"Annamari\" 1996) (\"Reetta\" 1992) (\"Vilma\" 1999));"
				  + "for tyttö in tytöt;"
				  + "do push\"nimi \"..tyttö[0]..\"; syntynyt \"..tyttö[1];"
				  + "done}"));
	}

	// Merkkijono-operaatiot

	@Test
	public void testStringLength() {
		assertEquals("14",
			     eval("main{push #\"Make M. Muikku\"}"));
		init();
		assertEquals("11",
			     eval("main{nimi:=\"Make Muikku\";push #nimi}"));
	}

	@Test
	public void testStringConcat() {
		assertEquals("Make M. Muikku",
			     eval("main{push \"Make \"..\"M. Muikku\"}"));
		init();
		assertEquals("Serkku S. Muikku",
			     eval("main{nimi:=\"S.\";push \"Serkku \"..nimi..\" Muikku\"}"));
	}

	// Listaoperaatiot

	@Test
	public void testListLength() {
		assertEquals("4",
			     eval("main{push #(\"Annamari\" \"Reetta\" \"Vilma\" \"Susanna\")}"));
		init();
		assertEquals("3",
			     eval("main{a:=(\"Reetta\" \"Vilma\" \"Susanna\");push #a}"));
	}

	@Test
	public void testListElement() {
		assertEquals("Reetta",
			     eval("main{push (\"Annamari\" \"Reetta\" \"Vilma\" \"Susanna\")[1]}"));
		init();
		assertEquals("Vilma",
			     eval("main{a:=(\"Annamari\" \"Reetta\" \"Vilma\" \"Susanna\");push a[2]}"));
	}

	@Test
	public void testListNegativeElement() {
		assertEquals("Susanna",
			     eval("main{push (\"Annamari\" \"Reetta\" \"Vilma\" \"Susanna\")[-1]}"));
		init();
		assertEquals("Annamari",
			     eval("main{a:=(\"Annamari\" \"Reetta\" \"Vilma\" \"Susanna\");push a[-4]}"));
	}

	@Test
	public void testListSlice() {
		assertEquals("(Reetta Vilma)",
			     eval("main{push (\"Annamari\" \"Reetta\" \"Vilma\" \"Susanna\")[1:3]}"));
		init();
		assertEquals("(Annamari Reetta)",
			     eval("main{a:=(\"Annamari\" \"Reetta\" \"Vilma\" \"Susanna\");push a[0:-2]}"));
	}

	@Test
	public void testListJoin() {
		assertEquals("Annamari_Reetta_Vilma_Susanna",
			     eval("main{push (\"Annamari\" \"Reetta\" \"Vilma\" \"Susanna\")&\"_\"}"));
		init();
		assertEquals("Annamari ja Reetta ja Vilma ja Susanna",
			     eval("main{a:=(\"Annamari\" \"Reetta\" \"Vilma\" \"Susanna\");push a&\" ja \"}"));
	}

	@Test
	public void testListJoinEmpty() {
		assertEquals("",
			     eval("main{push ()&\"_\"}"));
	}

	@Test
	public void testListJoinSingle() {
		assertEquals("Jaana",
			     eval("main{push (\"Jaana\")&\"_\"}"));
	}

	@Test
	public void testListConcat() {
		assertEquals("([1] [2] [3])",
			     eval("main{push \"[\"..(1 2 3)..\"]\"}"));
	}

	// Upotetut komennot

	@Test
	public void testStatementListExpression() {
		assertEquals("(a d e)",
			     eval("t{push\"a\"\"d\"\"e\"}main{push !(t)}"));
	}

	@Test
	public void testStatementSingleExpression() {
		assertEquals("Lilja",
			     eval("t{push\"Lilja\"}main{push ![t]}"));
	}

	@Test
	public void testSplit() {
		assertEquals("(sanna ja teemu jokela)",
			     eval("main{push !(split -s \"-\" \"sanna-ja-teemu-jokela\")}"));
	}

	// Nimettömät funktiot

	@Test
	public void testPassingAnonymousFunction() {
		assertEquals("Vilma on vielä nuori.",
			     eval("filter c{while pull -r v;do if c v;do push v;done;done}"
				  + "main{"
				  + "tytöt:=((\"Annamari\" 1996) (\"Reetta\" 1992) (\"Vilma\" 1999));"
				  + "tytöt|filter{|tyttö|;test tyttö[1] -gt 1996}|while pull -r tyttö;do "
				  + "push tyttö[0]..\" on vielä nuori.\";done"
				  + "}"));
	}

	// Argumentit

	@Test
	public void testArguments() {
		interpreter.interpret("main a b{push b a}",
				      Arrays.asList(RödaValue.valueFromString("Anne"),
						    RödaValue.valueFromString("Sanna")),
				      "<test>");
		assertEquals("Sanna,Anne", getResults());
	}

	@Test
	public void testArgumentsFlatten() {
		assertEquals("Anu,Riina", eval("f a b{push b a}main{A:=(\"Riina\" \"Anu\");f*A}"));
	}

	@Test
	public void testArgumentList() {
		interpreter.interpret("main a...{push a[1] a[0] #a}",
				      Arrays.asList(RödaValue.valueFromString("Venla"),
						    RödaValue.valueFromString("Eveliina")),
				      "<test>");
		assertEquals("Eveliina,Venla,2", getResults());
	}
}
