package org.kaivos.röda.type;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;

import org.kaivos.röda.Parser.Expression.CType;

public class RödaString extends RödaValue {
	private String text;

	private RödaString(String text) {
		assumeIdentity(STRING);
		this.text = text;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public String str() {
		return text;
	}

	@Override public long integer() {
		try {
			return Long.parseLong(text);
		} catch (NumberFormatException e) {
			error("can't convert '" + text + "' to a number");
			return -1;
		}
	}

	@Override public RödaValue length() {
		return RödaInteger.of(text.length());
	}

	@Override public RödaValue slice(RödaValue startVal, RödaValue endVal) {
		long start = startVal == null ? 0 : startVal.integer();
		long end = endVal == null ? text.length() : endVal.integer();
		if (start < 0) start = text.length()+start;
		if (end < 0) end = text.length()+end;
		if (end == 0 && start > 0) end = text.length();
		if (start > Integer.MAX_VALUE || end > Integer.MAX_VALUE)
			error("string index out of bounds: too large number: " + (start > end ? start : end));
		return of(text.substring((int) start, (int) end));
	}
	
	@Override
	public RödaValue callOperator(CType operator, RödaValue value) {
		if (operator == CType.MUL ? !value.is(INTEGER) : !value.is(STRING))
			error("can't " + operator.name() + " a " + typeString() + " and a " + value.typeString());
		switch (operator) {
		case MUL:
			String a = "";
			for (int i = 0; i < value.integer(); i++) a += this.str();
			return RödaString.of(a);
		case LT:
			return RödaBoolean.of(this.str().compareTo(value.str()) < 0);
		case GT:
			return RödaBoolean.of(this.str().compareTo(value.str()) > 0);
		case LE:
			return RödaBoolean.of(this.str().compareTo(value.str()) <= 0);
		case GE:
			return RödaBoolean.of(this.str().compareTo(value.str()) >= 0);
		default:
			return super.callOperator(operator, value);
		}
	}

	@Override public boolean strongEq(RödaValue value) {
		return value.is(STRING) && value.str().equals(text);
	}

	public static RödaString of(String text) {
		return new RödaString(text);
	}

}
