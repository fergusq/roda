package org.kaivos.röda.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
        RödaTest.class,
	LexerTest.class
})
public class RödaSuite {}
