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
import org.kaivos.röda.type.RödaString;

public class RödaTest {
	private ArrayList<RödaValue> results;
	
	@Before
	public void init() {
		results = new ArrayList<>();
		Interpreter.INTERPRETER.populateBuiltins();
	}

	@After
	public void cleanup() {
		/* TODO */
	}

	private String eval(String code) {
		RödaStream in = makeStream(v -> {}, () -> null, () -> {}, () -> true);
		RödaStream out = makeStream(v -> results.add(v), () -> null, () -> {}, () -> true);
		Interpreter.INTERPRETER.interpret(code, "<test>", in, out);
		return getResults();
	}

	private String getResults() {
		return results.stream().map(v -> v.str()).collect(joining(","));
	}

	// Merkkijonoliteraali

	@Test
	public void testNormalStringLiteral() {
		assertEquals("abba", eval("main{push\"abba\"}"));
	}

	@Test
	public void testBacktickStringLiteral() {
		assertEquals("abba", eval("main{push`abba`}"));
	}

	@Test
	public void testBacktickStringLiteralWithASubstitution() {
		assertEquals("abba 2 cabba", eval("main{x:=2;push`abba $x cabba`}"));
	}

	@Test
	public void testBacktickStringLiteralWithASubstitutionInBraces() {
		assertEquals("abba2cabba", eval("main{x:=2;push`abba${x}cabba`}"));
	}

	@Test
	public void testBacktickStringLiteralWithASugarVar() {
		assertEquals("abba 1 1 cabba,abba 2 2 cabba", eval("main{push 1,2|push`abba $_ $_1 cabba`}"));
	}
	
	// Peek-komento
	
	@Test
	public void testPeek() {
		assertEquals("10,10,7,2,2,2,45", eval("main{push(10, 7, 2, 45) | { push(peek()); push(pull()); push(pull()); push(peek()); push(peek()); push(pull()); push(peek()); }}"));
	}
	
	// Laskutoimitukset

	@Test
	public void test2Plus2Equals4() {
		assertEquals("4", eval("main{push(2+2);}"));
	}

	@Test
	public void testGrouping() {
		assertEquals("13", eval("main{push(1+(3+1)*3);}"));
	}

	@Test
	public void testVariablesInCalculation() {
		assertEquals("13", eval("main{a:=6;b:=1;b=2;push(1+a*b);}"));
	}

	@Test
	public void testMinusOperation() {
		assertEquals("3", eval("main{push(9-2*3);}"));
	}

	@Test
	public void testMinusOperationWithVariable() {
		assertEquals("8", eval("main{a:=4;push(20-a*3);}"));
	}

	@Test
	public void testCalculatorAbbreviation() {
		assertEquals("37", eval("main{a:=9;push 1+a*4;}"));
	}

	@Test
	public void testMultipleCalculatorAbbreviations() {
		assertEquals("35,8", eval("main{a:=9;push a*4-1,a-1;}"));
	}

	// Tietueet

	@Test
	public void testCreatingAndAccessingRecordInstance() {
		assertEquals("7,4", eval("record R{a:number;b:number}main{r:=new R;r.a=4;r.b=7;push r.b, r.a}"));
	}

	@Test
	public void testAccessingInheritedRecordFields() {
		assertEquals("9,5", eval("record R{a:number}record S:R{b:number};"
					 + "main{r:=new S;r.a=5;r.b=9;push r.b, r.a}"));
	}

	@Test
	public void testDefaultValues() {
		assertEquals("Isabella", eval("f{push \"Isabella\"}"
					      + "record R{a:string=f()}"
					      + "main{r:=new R;push r.a}"));
	}

	@Test
	public void testTypeparametrizationInRecords() {
		assertEquals("Elsa,Kyllikki", eval("record R<<T>>{t:T;l:list<<T>>}main{r:=new R<<string>>;"
						   + "r.t=\"Kyllikki\";r.l=new list<<string>>;r.l+=\"Elsa\";"
						   + "push r.l[0], r.t}"));
	}

	@Test
	public void testTypeparametrizationInSuperTypes() {
		assertEquals("Tiina,Amanda",
				eval("record R<<T>>{l:list<<T>>=new list<<T>>}record S<<U>>:R<<U>>{}"
						+ "main{s:=new S<<string>>;"
						+ "s.l+=\"Tiina\";s.l+=\"Amanda\";"
						+ "s.l}"));
	}

	@Test
	public void testSuperTypeArguments() {
		assertEquals("Iida,Irina",
				eval("record R(a,b){l:list=[a,b]}record S(a):R(a,\"Irina\"){}"
						+ "main{s:=new S(\"Iida\");"
						+ "s.l}"));
	}

	@Test(expected=RödaException.class)
	public void testFieldTypeparametrizationWithWrongTypes() {
		eval("record R<<T>>{t:T}main{r:=new R<<string>>;r.t=5}");
	}

	@Test(expected=RödaException.class)
	public void testListTypeparametrizationWithWrongTypes() {
		eval("record R<<T>>{l:list<<T>>}main{r:=new R<<string>>;r.l=new list<<list>>;r.l+=[]}");
	}

	@Test
	public void testTypeparameterScopingInDefaultValues() {
		assertEquals("Viivi", eval("record R<<T>>{l:list<<T>>=new list<<T>>}main{r:=new R<<string>>;"
					   + "r.l+=\"Viivi\";push r.l[0]}"));
	}

	@Test(expected=RödaException.class)
	public void testTypeparameterScopingInDefaultValuesWithWrongTypes() {
		eval("record R<<T>>{l:list<<T>>=new list<<T>>}main{r:=new R<<number>>;"
		     + "r.l+={}}");
	}

	@Test
	public void testTypeparameterScopingInMethods() {
		assertEquals("Sofia", eval("record R<<T>>{function f{push new T}}main{r:=new R<<list<<string>>>>;"
					   + "l:=r.f();l+=\"Sofia\";push l[0]}"));
	}

	@Test(expected=RödaException.class)
	public void testTypeparameterScopingInMethodsWithWrongTypes() {
		eval("record R<<T>>{function f{push new list<<T>>}}main{r:=new R<<function>>;"
		     + "l:=r.f();l+=24;push l[0]}");
	}

	@Test
	public void testTypeparametrizationInMethods() {
		assertEquals("Kaisa", eval("record R<<T>>{function f<<U>>{push new list<<U>>;push new list<<T>>}}"
					   + "main{r:=new R<<string>>;"
					   + "l:=[r.f<<list>>()];l[0]+=[];l[1]+=\"Kaisa\";push l[1][0]}"));
	}

	@Test(expected=RödaException.class)
	public void testOverridingTypeparameter() {
		eval("record R<<T>>{function f<<T>>{push new list<<T>>}}main{r:=new R<<string>>;"
		     + "l:=r.f<<number>>();l+=12;push l[0]}");
	}

	// Reflektio

	@Test
	public void testReflectRecordName() {
		assertEquals("Mimmi", eval("record Mimmi{}main{push((reflect Mimmi).name)}"));
	}

	@Test
	public void testReflectFieldName() {
		assertEquals("Miina", eval("record R{Miina:string}main{push((reflect R).fields[0].name)}"));
	}

	@Test
	public void testReflectFieldType() {
		assertEquals("S", eval("record R{Miina:S}record S{}main{push((reflect R).fields[0].type.name)}"));
	}

	@Test
	public void testReflectRecordAnnotation() {
		assertEquals("Salli", eval("function @A{push\"Salli\"}"
					   + "@A;record R{}main{push((reflect R).annotations[0])}"));
	}

	@Test
	public void testReflectFieldAnnotation() {
		assertEquals("Maisa", eval("function @A{push\"Maisa\"}"
					   + "record R{@A;f:number}main{push((reflect R).fields[0].annotations[0])}"));
	}

	/* README:n esimerkit (hieman muutettuina)*/

	// Funktiot

	@Test
	public void testSimplePullPushFunction() {
		assertEquals("auto,auto",
			     eval("duplicate{pull a;push a;push a;}main{push(\"auto\")|duplicate;}"));
	}

	@Test
	public void testVarargsFunction() {
		assertEquals("got abba,got tuuli,got joki",
			     eval("give words...{for word in words;do push\"got \"..word;done}"
				  + "main{give\"abba\",\"tuuli\",\"joki\"}"));
	}

	@Test
	public void testKwargsFunction() {
		assertEquals("abba,tuuli",
			     eval("pushdef text=\"abba\"{push text}"
				  + "main{pushdef;pushdef text=\"tuuli\"}"));
	}

	@Test
	public void testVarargsKwargsFunction() {
		assertEquals("hey abba,hey tuuli,hey joki,got pilvi",
			     eval("give words...,text=\"got \"{for word in words;do push text..word;done}"
				  + "main{give\"abba\",\"tuuli\",\"joki\",text=\"hey \";give\"pilvi\"}"));
	}

	@Test
	public void testTypeparametrizationInFunctions() {
		assertEquals("Leila", eval("f<<T>>{push new list<<T>>}main{"
					   + "l:=f<<string>>();l+=\"Leila\";push l[0]}"));
	}

	@Test(expected=RödaException.class)
	public void testTypeparametrizationInFunctionsWithWrongTypes() {
	        eval("f<<T>>{push new list<<T>>}main{"
		     + "l:=f<<string>>();l+=();push l[0]}");
	}

	@Test
	public void testParameterTypeChecking() {
		assertEquals("4451,[1, 2, 3],Lilli,Meri",
			     eval("give (a : number, b : list, c : string...) {return a, b, *c}"
				  + "main{give 4451, [1, 2, 3], \"Lilli\", \"Meri\"}"));
	}

	@Test(expected=RödaException.class)
	public void testParameterTypeCheckingWithWrongTypes() {
		eval("give (a : number, b : list, c : string...){return a, b, *c}"
		     + "main{give \"aasi\", new map, 11111, []}");
	}

	// Viittaukset

	@Test
	public void testPullingReference() {
		assertEquals("[abba, musiikki]",
			     eval("pull_twice &var{pull a1;pull a2;var=[a1, a2]}"
				  + "main{push(\"abba\",\"musiikki\")|pull_twice v;push v}"));
	}

	@Test
	public void testPullingReferenceInAnonymousFunction() {
		assertEquals("17", eval("main{push(17)|{|&a|;pull a}b;push b}"));
	}

	@Test
	public void testSettingReference() {
		assertEquals("20", eval("main{push(17)|{|&a|;a:=20}b;push b}"));
	}

	// Muuttujat

	@Test
	public void testVariableFlagCreate() {
		assertEquals("tieto.txt", eval("main{tiedosto:=\"tieto.txt\";push tiedosto}"));
		init();
		assertEquals("73", eval("main{ikä:=73;push ikä}"));
		init();
		assertEquals("[Annamari, Reetta, Vilma]",
			     eval("main{tytöt:=[\"Annamari\", \"Reetta\", \"Vilma\"];push tytöt}"));
	}

	@Test
	public void testVariableFlagSet() {
		assertEquals("74", eval("main{ikä:=73;ikä=74;push ikä}"));
	}

	@Test
	public void testVariableFlagAdd() {
		assertEquals("[Annamari, Reetta, Vilma, Maija]",
			     eval("main{tytöt:=[\"Annamari\", \"Reetta\", \"Vilma\"];"
				  + "tytöt+=\"Maija\";push tytöt}"));
	}

	@Test
	public void testVariableFlagPut() {
		assertEquals("[Annamari, Liisa, Vilma]",
			     eval("main{tytöt:=[\"Annamari\", \"Reetta\", \"Vilma\"];"
				  + "tytöt[1]=\"Liisa\";push tytöt}"));
	}

	@Test
	public void testVariableFlagIncDec() {
		assertEquals("72,11",
			     eval("main{ikä:=73;voimat:=10;ikä--;voimat++;push ikä, voimat}"));
	}

	@Test(expected=RödaException.class)
	public void testPushingStringVariable() {
		assertEquals("abba",
			     eval("main{v:=\"abba\";v}"));
	}

	@Test
	public void testPushingListVariable() {
		assertEquals("joki,virta",
			     eval("main{l:=[\"joki\", \"virta\"];l}"));
	}

	@Test
	public void testPushingList() {
		assertEquals("rivi1\n,rivi2\n,rivi3\n",
			     eval("main{[\"rivi1\\n\", \"rivi2\\n\", \"rivi3\\n\"]}"));
	}

	// Ohjausrakenteet

	@Test
	public void testIf() {
		assertEquals("Olet liian nuori!\n",
			     eval("main{ikä:=16;if [ ikä < 18 ];do push\"Olet liian nuori!\\n\";done}"));
	}

	@Test
	public void testIfWithFalseCondition() {
		assertEquals("",
			     eval("main{ikä:=24;if [ ikä < 18 ];do push\"Olet liian nuori!\\n\";done}"));
	}

	@Test
	public void testIfElse() {
		assertEquals("Tervetuloa!\n",
			     eval("main{ikä:=24;if [ ikä >= 18 ];do push\"Tervetuloa!\\n\";"
				  + "else push\"Olet liian nuori!\\n\";done}"));
	}

	@Test
	public void testIfElseWithFalseCondition() {
		assertEquals("Olet liian nuori!\n",
			     eval("main{ikä:=16;if [ ikä >= 18 ];do push\"Tervetuloa!\\n\";"
				  + "else push\"Olet liian nuori!\\n\";done}"));
	}

	@Test
	public void testFor() {
		assertEquals("nimi Annamari; syntynyt 1996,nimi Reetta; syntynyt 1992,nimi Vilma; syntynyt 1999",
			     eval("main{tytöt:=[[\"Annamari\", 1996], [\"Reetta\", 1992], [\"Vilma\", 1999]];"
				  + "for tyttö in tytöt;"
				  + "do push\"nimi \"..tyttö[0]..\"; syntynyt \"..tyttö[1];"
				  + "done}"));
	}

	@Test
	public void testPullingFor() {
		assertEquals("nimi Annamari; syntynyt 1996,nimi Reetta; syntynyt 1992,nimi Vilma; syntynyt 1999",
			     eval("main{[[\"Annamari\", 1996], [\"Reetta\", 1992], [\"Vilma\", 1999]]|"
				  + "for tyttö;"
				  + "do push\"nimi \"..tyttö[0]..\"; syntynyt \"..tyttö[1];"
				  + "done}"));
	}

	@Test
	public void testForIf() {
		assertEquals("nimi Annamari; syntynyt 1996,nimi Vilma; syntynyt 1999",
			     eval("main{tytöt:=[[\"Annamari\", 1996], [\"Reetta\", 1992], [\"Vilma\", 1999]];"
				  + "for tyttö in tytöt if [ tyttö[1] > 1995 ];"
				  + "do push\"nimi \"..tyttö[0]..\"; syntynyt \"..tyttö[1];"
				  + "done}"));
	}

	@Test
	public void testSuffixFor() {
		assertEquals("Reetta on paikalla,Vilma on paikalla,Annamari on paikalla",
			     eval("main{tytöt:=[\"Reetta\", \"Vilma\", \"Annamari\"];"
				  + "push tyttö..\" on paikalla\" for tyttö in tytöt}"));
	}

	@Test
	public void testSuffixForIf() {
		assertEquals("Reetta on kiireinen,Vilma on kiireinen",
			     eval("main{tytöt:=[[\"Reetta\", 1], [\"Vilma\", 1], [\"Annamari\", 0]];"
				  + "push tyttö[0]..\" on kiireinen\" for tyttö in tytöt"
				  + " if [ tyttö[1] = 1 ]}"));
	}
	
	@Test
	public void testSugarFor() {
		assertEquals("got Maisa,got Anne",
				eval("f x{push\"got \"..x;}main{push(\"Maisa\",\"Anne\")|f(_)}"));
	}

	@Test
	public void testSuffixIf() {
		assertEquals("5",
			     eval("main{hinta:=10;alennus=true();korotus=false();"
				  + "hinta //= 2 if push alennus;"
				  + "hinta += 10 if push korotus;"
				  + "push hinta}"));
	}

	@Test
	public void testTrivialReturn() {
		assertEquals("a",
			     eval("main{push\"a\";return;push\"b\"}"));
	}

	@Test
	public void testNestedReturn() {
		assertEquals("b,d",
			     eval("main{push\"b\";if true;do push\"d\";if true;do try return;done;done;push\"a\"}"));
	}

	@Test
	public void testReturnInTry() {
		assertEquals("b",
			     eval("main{push\"b\";try return;push\"a\"}"));
	}

	@Test
	public void testReturnInIf() {
		assertEquals("b",
			     eval("main{push\"b\";if true;do return;done;push\"a\"}"));
	}

	@Test
	public void testReturningValue() {
		assertEquals("Laila,Ella,Tuuli",
			     eval("f{return\"Ella\";push\"Salla\"}main{push\"Laila\";f;push\"Tuuli\"}"));
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
			     eval("main{push #[\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"]}"));
		init();
		assertEquals("3",
			     eval("main{a:=[\"Reetta\", \"Vilma\", \"Susanna\"];push #a}"));
	}

	@Test
	public void testListElement() {
		assertEquals("Reetta",
			     eval("main{push([\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"][1])}"));
		init();
		assertEquals("Vilma",
			     eval("main{a:=[\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"];push a[2]}"));
	}

	@Test
	public void testListNegativeElement() {
		assertEquals("Susanna",
			     eval("main{push([\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"][-1])}"));
		init();
		assertEquals("Annamari",
			     eval("main{a:=[\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"];push a[-4]}"));
	}

	@Test
	public void testListSlice() {
		assertEquals("[Reetta, Vilma]",
			     eval("main{push([\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"][1:3])}"));
		init();
		assertEquals("[Annamari, Reetta]",
			     eval("main{a:=[\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"];push a[0:-2]}"));
	}

	@Test
	public void testListSliceStep() {
		assertEquals("[Annamari, Vilma]",
			     eval("main{push([\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"][::2])}"));
	}

	@Test
	public void testListSliceNegativeStep() {
		assertEquals("[Susanna, Reetta]",
			     eval("main{push([\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"][3:0:-2])}"));
	}

	@Test
	public void testListSetSlice() {
		assertEquals("[Annamari, Janna, Tuuli, Susanna]",
			     eval("main{l:=[\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"];l[1:3]=[\"Janna\", \"Tuuli\"];push(l)}"));
	}
	
	@Test
	public void testListSetSliceStep() {
		assertEquals("[Annamari, Janna, Vilma, Tuuli]",
			     eval("main{l:=[\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"];l[1::2]=[\"Janna\", \"Tuuli\"];push(l)}"));
	}
	
	@Test
	public void testListSetSliceNegativeStep() {
		assertEquals("[Annamari, Tuuli, Janna, Susanna]",
			     eval("main{l:=[\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"];l[2:0:-1]=[\"Janna\", \"Tuuli\"];push(l)}"));
	}

	@Test
	public void testListDelSlice() {
		assertEquals("[Annamari, Susanna]",
			     eval("main{l:=[\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"];del l[1:3];push(l)}"));
	}

	@Test
	public void testListDelSliceStep() {
		assertEquals("[Reetta, Vilma]",
			     eval("main{l:=[\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"];del l[::3];push(l)}"));
	}

	@Test
	public void testListDelSliceNegativeStep() {
		assertEquals("[Reetta, Vilma]",
			     eval("main{l:=[\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"];del l[-1::-3];push(l)}"));
	}

	@Test
	public void testListJoin() {
		assertEquals("Annamari_Reetta_Vilma_Susanna",
			     eval("main{push([\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"]&\"_\")}"));
		init();
		assertEquals("Annamari ja Reetta ja Vilma ja Susanna",
			     eval("main{a:=[\"Annamari\", \"Reetta\", \"Vilma\", \"Susanna\"];push a&\" ja \"}"));
	}

	@Test
	public void testListJoinEmpty() {
		assertEquals("",
			     eval("main{push([]&\"_\")}"));
	}

	@Test
	public void testListJoinSingle() {
		assertEquals("Jaana",
			     eval("main{push([\"Jaana\"]&\"_\")}"));
	}

	@Test
	public void testListConcat() {
		assertEquals("[[1, 2, 3]]",
			     eval("main{push \"[\"..[1, 2, 3]..\"]\"}"));
	}

	@Test
	public void testListConcatChildren() {
		assertEquals("[[1], [2], [3]]",
			     eval("main{push \"[\"...[1, 2, 3]...\"]\"}"));
	}

	@Test
	public void testListInOperator() {
		assertEquals("joo,ei",
			     eval("main{l:=[\"Aino\", \"Anne\", \"Alina\"];"
				  + "if [ \"Anne\" in l ]; do push \"joo\"; else push \"ei\"; done;"
				  + "if [ \"Henna\" in l ]; do push \"joo\"; else push \"ei\"; done}"));
	}

	// Karttaoperaatiot

	@Test
	public void testMapLength() {
		assertEquals("3",
			     eval("main{a:=new map;a[\"Reetta\"]=19;a[\"Vilma\"]=23;a[\"Susanna\"]=14;push #a}"));
	}

	@Test
	public void testMapContains() {
		assertEquals("<true>",
			     eval("main{a:=new map;a[\"Reetta\"]=19;a[\"Vilma\"]=23;a[\"Susanna\"]=14;"
				  + "push a[\"Vilma\"]?}"));
		init();
		assertEquals("<false>",
			     eval("main{a:=new map;a[\"Reetta\"]=19;a[\"Vilma\"]=23;a[\"Susanna\"]=14;"
				  + "push a[\"Elina\"]?}"));
	}

	@Test
	public void testMapGet() {
		assertEquals("23",
			     eval("main{a:=new map;a[\"Reetta\"]=19;a[\"Vilma\"]=23;a[\"Susanna\"]=14;push a[\"Vilma\"]}"));
	}

	@Test(expected=RödaException.class)
	public void testMapGetUnknownKey() {
		assertEquals("3",
			     eval("main{a:=new map;a[\"Reetta\"]=19;push a[\"Susanna\"]}"));
	}

	// Upotetut komennot

	@Test
	public void testStatementListExpression() {
		assertEquals("[a, d, e]",
			     eval("t{push\"a\",\"d\",\"e\"}main{push([t()])}"));
	}

	@Test
	public void testStatementSingleExpression() {
		assertEquals("Lilja",
			     eval("t{push\"Lilja\"}main{push t()}"));
	}

	@Test(expected=RödaException.class)
	public void testStatementSingleExpressionAbbreviationWithMultipleReturnValues() {
		eval("t{push\"Elina\",\"Lilja\"}main{push t()}");
	}

	// Sisäänrakennetut funktiot
	
	@Test
	public void testSplit() {
		assertEquals("[sanna, ja, teemu, jokela]",
			     eval("main{push([split(\"sanna-ja-teemu-jokela\", sep=\"-\")])}"));
		init();
		assertEquals("[sanna, ja, teemu, jokela]",
			     eval("main{push([split(\"sanna ja teemu jokela\")])}"));
	}
	
	@Test
	public void testInterleave() {
		assertEquals("1,4,7,2,5,8,3,6,9", eval("main{interleave([1,2,3],[4,5,6],[7,8,9])}"));
	}
	
	@Test(expected=RödaException.class)
	public void testInterleaveDifferentListSizes() {
		eval("main{interleave([1,2,3],[4],[7,8,9,10])}");
	}

	// Nimettömät funktiot

	@Test
	public void testPassingAnonymousFunction() {
		assertEquals("Vilma on vielä nuori.",
			     eval("filter c{for v;do if c v;do push v;done;done}"
				  + "main{"
				  + "tytöt:=[[\"Annamari\", 1996], [\"Reetta\", 1992], [\"Vilma\", 1999]];"
				  + "tytöt|filter{|tyttö|;[ tyttö[1] > 1996 ]}|for tyttö;do "
				  + "push tyttö[0]..\" on vielä nuori.\";done"
				  + "}"));
	}
	
	// Lausekekomennot

	@Test
	public void testExpressionCommandInIf() {
		assertEquals("Vilma on vielä nuori.",
			     eval("main{"
				  + "tytöt:=[[\"Annamari\", 1996], [\"Reetta\", 1992], [\"Vilma\", 1999]];"
				  + "for tyttö in tytöt; do if [ tyttö[1] > 1996 ]; do push tyttö[0]..\" on vielä nuori.\""
				  + ";done;done}"));
	}

	// Argumentit

	@Test
	public void testArguments() {
		RödaStream in = makeStream(v -> {}, () -> null, () -> {}, () -> true);
		RödaStream out = makeStream(v -> results.add(v), () -> null, () -> {}, () -> true);
		Interpreter.INTERPRETER.interpret("main a, b{push b, a}",
				      Arrays.asList(RödaString.of("Anne"),
						    RödaString.of("Sanna")),
				      "<test>", in, out);
		assertEquals("Sanna,Anne", getResults());
	}

	@Test
	public void testArgumentsFlatten() {
		assertEquals("Anu,Riina", eval("f a, b{push b, a}main{A:=[\"Riina\", \"Anu\"];f*A}"));
	}

	@Test
	public void testArgumentList() {
		RödaStream in = makeStream(v -> {}, () -> null, () -> {}, () -> true);
		RödaStream out = makeStream(v -> results.add(v), () -> null, () -> {}, () -> true);
		Interpreter.INTERPRETER.interpret("main a...{push a[1], a[0], #a}",
				      Arrays.asList(RödaString.of("Venla"),
						    RödaString.of("Eveliina")),
				      "<test>", in, out);
		assertEquals("Eveliina,Venla,2", getResults());
	}
}
