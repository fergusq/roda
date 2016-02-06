package org.kaivos.röda.type;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;

public class RödaFlag extends RödaValue {
	private String flag;

	private RödaFlag(String flag) {
		assumeIdentity(FLAG);
		this.flag = flag;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public String str() {
		return flag;
	}

	@Override public boolean isFlag() {
		return true;
	}

	@Override public boolean isFlag(String text) {
		return flag.equals(text);
	}

	@Override public boolean strongEq(RödaValue value) {
		return value.isFlag(flag);
	}

	public static RödaFlag of(String flag) {
		return new RödaFlag(flag);
	}
}
