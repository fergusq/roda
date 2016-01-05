package org.kaivos.röda.type;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;

public class RödaBoolean extends RödaValue {
	private boolean bool;

	private RödaBoolean(boolean bool) {
		assumeIdentity("bool");
		this.bool = bool;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public String str() {
		return bool ? "true" : "false";
	}

	@Override public boolean bool() {
		return bool;
	}

	@Override public boolean isBoolean() {
		return true;
	}

	@Override public String typeString() {
		return "bool";
	}

	@Override public boolean strongEq(RödaValue value) {
		return value.isBoolean() && value.bool() == bool;
	}

	public static RödaBoolean of(boolean value) {
		return new RödaBoolean(value);
	}
}
