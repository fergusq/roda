package org.kaivos.röda.type;

import org.kaivos.röda.Parser;
import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.typeMismatch;

public class RödaFloating extends RödaValue {
	private double number;

	private RödaFloating(double number) {
		assumeIdentity(FLOATING);
		assumeIdentity(NUMBER);
		this.number = number;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public String str() {
		return String.valueOf(number);
	}

	@Override public double floating() {
	        return number;
	}
	
	@Override
	public RödaValue callOperator(Parser.ExpressionTree.CType operator, RödaValue value) {
		switch (operator) {
		case NEG:
			return RödaFloating.of(-this.floating());
		default:
		}
		if (!value.is(NUMBER)) typeMismatch("can't " + operator.name() + " " + typeString() + " and " + value.typeString());
		switch (operator) {
		case POW:
			return RödaFloating.of(Math.pow(this.floating(), value.floating()));
		case MUL:
			return RödaFloating.of(this.floating()*value.floating());
		case DIV:
			return RödaFloating.of(this.floating()/value.floating());
		case IDIV:
			return RödaInteger.of((long) (this.floating()/value.floating()));
		case MOD:
			return RödaFloating.of(this.floating()%value.floating());
		case ADD:
			return RödaFloating.of(this.floating()+value.floating());
		case SUB:
			return RödaFloating.of(this.floating()-value.floating());
		case LT:
			return RödaBoolean.of(this.floating()<value.floating());
		case GT:
			return RödaBoolean.of(this.floating()>value.floating());
		case LE:
			return RödaBoolean.of(this.floating()<=value.floating());
		case GE:
			return RödaBoolean.of(this.floating()>=value.floating());
		default:
			return super.callOperator(operator, value);
		}
	}

	@Override public boolean strongEq(RödaValue value) {
		return value.is(FLOATING) && value.floating() == number || value.is(INTEGER) && value.integer() == number;
	}
	
	@Override
	public int hashCode() {
		return Double.hashCode(number);
	}

	public static RödaFloating of(double number) {
		return new RödaFloating(number);
	}
}
