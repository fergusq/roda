package org.kaivos.röda.type;

import org.kaivos.röda.Interpreter.RödaScope;

import static org.kaivos.röda.Interpreter.outOfBounds;
import static org.kaivos.röda.Interpreter.unknownName;

import java.util.Optional;

import org.kaivos.röda.RödaValue;

public class RödaNamespace extends RödaValue {

	private RödaScope scope;
	
	private RödaNamespace(RödaScope scope) {
		assumeIdentity(NAMESPACE);
		this.scope = scope;
	}
	
	@Override
	public RödaValue copy() {
		return this;
	}

	@Override
	public String str() {
		return "<" + typeString() + " instance " + hashCode() + ">";
	}

	@Override public void setField(String name, RödaValue value) {
		scope.setLocal(name, value);
	}

	@Override public RödaValue getField(String name) {
		RödaValue value = scope.resolve(name);
		if (value == null)
			unknownName("variable '" + name + "' not found in namespace");
		return value;
	}

	@Override public RödaValue get(RödaValue indexVal) {
		String index = indexVal.str();
		RödaValue value = scope.resolve(index);
		if (value == null) outOfBounds("variable '" + index + "' not found in namespace");
		return value;
	}

	@Override public void set(RödaValue indexVal, RödaValue value) {
		String index = indexVal.str();
		scope.setLocal(index, value);
	}

	@Override public RödaValue contains(RödaValue indexVal) {
		String index = indexVal.str();
		return RödaBoolean.of(scope.resolve(index) != null);
	}
	
	@Override public RödaScope scope() {
		return scope;
	}
	
	@Override
	public int hashCode() {
		return scope.hashCode();
	}
	
	public static RödaNamespace of(RödaScope scope) {
		return new RödaNamespace(scope);
	}
	
	public static RödaNamespace empty() {
		return new RödaNamespace(new RödaScope(Optional.empty()));
	}

}
