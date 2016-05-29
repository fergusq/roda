package org.kaivos.röda.type;

import org.kaivos.röda.RödaValue;

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

	@Override public boolean strongEq(RödaValue value) {
		return value.is(FLAG) && value.str().equals(flag);
	}

	public static RödaFlag of(String flag) {
		return new RödaFlag(flag);
	}
}
