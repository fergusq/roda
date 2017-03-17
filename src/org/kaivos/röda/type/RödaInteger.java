package org.kaivos.röda.type;

import org.kaivos.röda.Parser;
import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.typeMismatch;

public class RödaInteger extends RödaValue {
	private long number;

	private RödaInteger(long number) {
		assumeIdentity(INTEGER);
		assumeIdentity(NUMBER);
		this.number = number;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public String str() {
		return String.valueOf(number);
	}

	@Override public long integer() {
	        return number;
	}

	@Override public double floating() {
	        return number;
	}
	
	@Override
	public RödaValue callOperator(Parser.ExpressionTree.CType operator, RödaValue value) {
		switch (operator) {
		case NEG:
			return RödaInteger.of(-this.integer());
		case BNOT:
			return RödaInteger.of(~this.integer());
		default:
		}
		if (value.is(FLOATING)) return RödaFloating.of(this.integer()).callOperator(operator, value);
		// TODO: ^ virheviestit eivät näyttävät tyypin olevan floating
		if (!value.is(INTEGER)) typeMismatch("can't " + operator.name() + " " + typeString() + " and " + value.typeString());
		switch (operator) {
		case POW:
			return RödaInteger.of((long) Math.pow(this.integer(), value.integer()));
		case MUL:
			return RödaInteger.of(this.integer()*value.integer());
		case DIV:
			return RödaFloating.of((double) this.integer()/value.integer());
		case IDIV:
			return RödaInteger.of(this.integer()/value.integer());
		case MOD:
			return RödaInteger.of(this.integer()%value.integer());
		case ADD:
			return RödaInteger.of(this.integer()+value.integer());
		case SUB:
			return RödaInteger.of(this.integer()-value.integer());
		case BAND:
			return RödaInteger.of(this.integer()&value.integer());
		case BOR:
			return RödaInteger.of(this.integer()|value.integer());
		case BXOR:
			return RödaInteger.of(this.integer()^value.integer());
		case BLSHIFT:
			return RödaInteger.of(this.integer()<<value.integer());
		case BRSHIFT:
			return RödaInteger.of(this.integer()>>value.integer());
		case BRRSHIFT:
			return RödaInteger.of(this.integer()>>>value.integer());
		case LT:
			return RödaBoolean.of(this.integer()<value.integer());
		case GT:
			return RödaBoolean.of(this.integer()>value.integer());
		case LE:
			return RödaBoolean.of(this.integer()<=value.integer());
		case GE:
			return RödaBoolean.of(this.integer()>=value.integer());
		default:
			return super.callOperator(operator, value);
		}
	}

	@Override public boolean strongEq(RödaValue value) {
		return value.is(INTEGER) && value.integer() == number;
	}
	
	@Override
	public int hashCode() {
		return Long.hashCode(number);
	}

	public static RödaInteger of(long number) {
		return new RödaInteger(number);
	}
}
