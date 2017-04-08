package org.kaivos.röda.type;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function;

public class RödaFunction extends RödaValue {
	private Function function;
	private RödaScope localScope;

	private RödaFunction(Function function) {
		assumeIdentity(FUNCTION);
		this.function = function;
		this.localScope = null;
	}

	private RödaFunction(Function function, RödaScope localScope) {
		assumeIdentity("function");
		this.function = function;
		this.localScope = localScope;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public Function function() {
		return function;
	}

	@Override public String str() {
		return "<function '"+function.name+"'>";
	}

	@Override public RödaScope localScope() {
		return localScope;
	}

	@Override public boolean strongEq(RödaValue value) {
		return value.is(FUNCTION) && !value.is(NFUNCTION) && value.function() == function;
	}
	
	@Override
	public int hashCode() {
		return function.hashCode() + localScope.hashCode();
	}

	public static RödaFunction of(Function function) {
		return new RödaFunction(function);
	}

	public static RödaFunction of(Function function, RödaScope localScope) {
		return new RödaFunction(function, localScope);
	}
}
