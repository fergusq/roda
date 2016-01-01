package org.kaivos.röda.test;

import org.junit.runner.*;
import org.junit.runner.notification.*;

public class TestRunner {
	public static void main(String[] args) {
		Result r = JUnitCore.runClasses(RödaTest.class, LexerTest.class);
	        
		for (Failure f : r.getFailures()) {
			System.out.println(f.toString());
		}
	}
}
