package org.kaivos.röda.type;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Interpreter.RödaScope;
import static org.kaivos.röda.Parser.Function;

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

	@Override public boolean isFunction() {
		return true;
	}

	@Override public boolean strongEq(RödaValue value) {
		return value.isFunction() && !value.isNativeFunction() && value.function() == function;
	}

	public static RödaFunction of(Function function) {
		return new RödaFunction(function);
	}

	public static RödaFunction of(Function function, RödaScope localScope) {
		return new RödaFunction(function, localScope);
	}
}
