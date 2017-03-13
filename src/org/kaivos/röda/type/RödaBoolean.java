package org.kaivos.röda.type;

import org.kaivos.röda.Parser;
import org.kaivos.röda.RödaValue;

public class RödaBoolean extends RödaValue {
	private boolean bool;

	private RödaBoolean(boolean bool) {
		assumeIdentity(BOOLEAN);
		this.bool = bool;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public String str() {
		return bool ? "<true>" : "<false>";
	}

	@Override public boolean bool() {
		return bool;
	}
	
	@Override
	public RödaValue callOperator(Parser.ExpressionTree.CType operator, RödaValue value) {
		switch (operator) {
		case NOT:
			return RödaBoolean.of(!bool);
		default:
			return super.callOperator(operator, value);
		}
	}

	@Override public boolean strongEq(RödaValue value) {
		return value.is(BOOLEAN) && value.bool() == bool;
	}
	
	@Override
	public int hashCode() {
		return Boolean.hashCode(bool);
	}

	public static RödaBoolean of(boolean value) {
		return new RödaBoolean(value);
	}
}
