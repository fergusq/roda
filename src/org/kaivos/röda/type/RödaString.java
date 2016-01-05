package org.kaivos.röda.type;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;

public class RödaString extends RödaValue {
	private String text;

	private RödaString(String text) {
		assumeIdentity("string");
		this.text = text;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public String str() {
		return text;
	}

	@Override public int num() {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			error("can't convert '" + text + "' to a number");
			return -1;
		}
	}

	@Override public RödaValue length() {
		return RödaNumber.of(text.length());
	}

	@Override public String typeString() {
		return "string";
	}

	@Override public boolean isString() {
		return true;
	}

	@Override public boolean isNumber() {
		return true;
	}

	@Override public boolean strongEq(RödaValue value) {
		return value.isString() && value.str().equals(value);
	}

	public static RödaString of(String text) {
		return new RödaString(text);
	}
}
