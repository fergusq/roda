package org.kaivos.röda.type;

import static java.util.stream.Collectors.toList;
import static org.kaivos.röda.Interpreter.outOfBounds;
import static org.kaivos.röda.Interpreter.typeMismatch;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.kaivos.röda.Parser.ExpressionTree.CType;
import org.kaivos.röda.RödaValue;

public class RödaString extends RödaValue {
	private String text;
	private Pattern pattern;

	private RödaString(String text) {
		assumeIdentity(STRING);
		this.text = text;
	}
	
	private RödaString(Pattern pattern) {
		this(pattern.pattern());
		this.pattern = pattern;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public String str() {
		return text;
	}
	
	@Override public Pattern pattern() {
		if (pattern != null) return pattern;
		else return super.pattern();
	}

	@Override public long integer() {
		try {
			return Long.parseLong(text);
		} catch (NumberFormatException e) {
			typeMismatch("can't convert '" + text + "' to a number");
			return -1;
		}
	}

	@Override public RödaValue length() {
		return RödaInteger.of(text.length());
	}

	@Override public RödaValue slice(RödaValue startVal, RödaValue endVal, RödaValue stepVal) {
		long step = stepVal == null ? 1 : stepVal.integer();
		long start = startVal != null ? startVal.integer() : step > 0 ? 0 : -1;
		if (start < 0) start = text.length()+start;
		long end;
		if (endVal == null) {
			if (step < 0) end = -1;
			else end = text.length();
		}
		else {
			end = endVal.integer();
			if (end < 0) end = text.length()+end;
			if (step > 0 && end == 0 && start > 0) end = text.length();
		}
		if (start > Integer.MAX_VALUE || end > Integer.MAX_VALUE)
			outOfBounds("string index out of bounds: too large number: " + (start > end ? start : end));
		if (step == 1)
			return of(text.substring((int) start, (int) end));
		StringBuilder newString = new StringBuilder();
		if (step > 0)
			for (int i = (int) start; i < end; i += step) newString.append(text.charAt(i));
		else if (step < 0)
			for (int i = (int) start; i > end; i += step) newString.append(text.charAt(i));
		return of(newString.toString());
	}
	
	@Override public RödaValue containsValue(RödaValue seq) {
		return RödaBoolean.of(text.indexOf(seq.str()) >= 0);
	}
	
	@Override
	public RödaValue callOperator(CType operator, RödaValue value) {
		if (operator == CType.MUL ? !value.is(INTEGER) : !value.is(STRING))
			typeMismatch("can't " + operator.name() + " " + typeString() + " and " + value.typeString());
		switch (operator) {
		case MUL:
			String a = "";
			for (int i = 0; i < value.integer(); i++) a += this.str();
			return RödaString.of(a);
		case DIV:
			return RödaList.of(Arrays.stream(this.str().split(value.str())).map(RödaString::of).collect(toList()));
		case LT:
			return RödaBoolean.of(this.str().compareTo(value.str()) < 0);
		case GT:
			return RödaBoolean.of(this.str().compareTo(value.str()) > 0);
		case LE:
			return RödaBoolean.of(this.str().compareTo(value.str()) <= 0);
		case GE:
			return RödaBoolean.of(this.str().compareTo(value.str()) >= 0);
		case MATCHES:
			if (!value.is(STRING)) typeMismatch("tried to MATCH " + value.typeString());
			if (((RödaString) value).pattern != null)
				return RödaBoolean.of(((RödaString) value).pattern.matcher(text).matches());
			else
				return RödaBoolean.of(text.matches(value.str()));
		case NO_MATCH:
			if (!value.is(STRING)) typeMismatch("tried to NO_MATCH " + value.typeString());
			if (((RödaString) value).pattern != null)
				return RödaBoolean.of(!((RödaString) value).pattern.matcher(text).matches());
			else
				return RödaBoolean.of(!text.matches(value.str()));
		default:
			return super.callOperator(operator, value);
		}
	}

	@Override public boolean strongEq(RödaValue value) {
		return value.is(STRING) && value.str().equals(text);
	}
	
	@Override
	public int hashCode() {
		return text.hashCode();
	}

	public static RödaString of(String text) {
		return new RödaString(text);
	}

	public static RödaString of(Pattern pattern) {
		return new RödaString(pattern);
	}

}
