package org.kaivos.röda.type;

import org.kaivos.röda.Parser;
import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;

public class RödaInteger extends RödaValue {
	private long number;

	private RödaInteger(long number) {
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
	
	@Override
	public RödaValue callOperator(Parser.Expression.CType operator, RödaValue value) {
		if (!this.is(NUMBER)) error("tried to " + operator.name() + " a " + this.typeString());
		if (!value.is(NUMBER)) error("tried to " + operator.name() + " a " + value.typeString());
		switch (operator) {
		case MUL:
			return RödaInteger.of(this.integer()*value.integer());
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
		return value.is(NUMBER) && value.integer() == number;
	}

	public static RödaInteger of(long number) {
		return new RödaInteger(number);
	}
}
