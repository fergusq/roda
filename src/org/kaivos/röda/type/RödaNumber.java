package org.kaivos.röda.type;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;

public class RödaNumber extends RödaValue {
	private int number;

	private RödaNumber(int number) {
		assumeIdentity("number");
		this.number = number;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public String str() {
		return String.valueOf(number);
	}

	@Override public int num() {
	        return number;
	}

	@Override public boolean isString() {
		return true;
	}

	@Override public boolean isNumber() {
		return true;
	}

	@Override public boolean strongEq(RödaValue value) {
		return value.isNumber() && value.num() == number;
	}

	public static RödaNumber of(int number) {
		return new RödaNumber(number);
	}
}
