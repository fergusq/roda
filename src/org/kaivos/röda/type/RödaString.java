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

	@Override public RödaValue slice(RödaValue startVal, RödaValue endVal) {
		int start = startVal == null ? 0 : startVal.num();
		int end = endVal == null ? text.length() : endVal.num();
		if (start < 0) start = text.length()+start;
		if (end < 0) end = text.length()+end;
		if (end == 0 && start > 0) end = text.length();
		return of(text.substring(start, end));
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
