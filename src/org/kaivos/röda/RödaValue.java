package org.kaivos.röda;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import static java.util.stream.Collectors.joining;

import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Interpreter.RödaScope;
import static org.kaivos.röda.Interpreter.RödaStream;
import static org.kaivos.röda.Parser.Function;
import static org.kaivos.röda.Parser.Parameter;

public class RödaValue {
	private enum Type {
		FUNCTION,
		NATIVE_FUNCTION,
		STRING,
		NUMBER,
		BOOLEAN,
		REFERENCE,
		LIST
	}
	
	private Type type;
	
	// STRING
	String text;

	// INT
	int number;

	// BOOLEAN
	boolean bool;
	
	// FUNCTION
	Function function;
	
	// NATIVE_FUNCTION
	NativeFunction nfunction;
	
	// REFERENCE
	String target;
	RödaScope scope;
	
	// LIST
	List<RödaValue> list;
	
	RödaValue() {} // käytä apufunktioita
	
	RödaValue copy() {
		RödaValue val = new RödaValue();
		val.type = type;
		val.text = text;
		val.number = number;
		val.bool = bool;
		val.function = function;
		val.nfunction = nfunction;
		val.target = target;
		val.scope = scope;
		if (list != null) {
			val.list = new ArrayList<>(list.size());
			for (RödaValue item : list) val.list.add(item);
		} else val.list = null;
		return val;
	}
	
	String str() {
		if (type == Type.BOOLEAN) return bool ? "true" : "false";
		if (type == Type.STRING) return text;
		if (type == Type.NUMBER) return ""+number;
		if (type == Type.FUNCTION) return "<function '"+function.name+"'>";
		if (type == Type.NATIVE_FUNCTION) return "<function '"+nfunction.name+"'>";
		if (type == Type.REFERENCE) {
			return "&" + target;
		}
		if (type == Type.LIST) {
			return "(" + list.stream().map(RödaValue::str).collect(joining(" ")) + ")";
		}
		error("unknown type " + type);
		return null;
	}
	
	boolean bool() {
		if (type == Type.BOOLEAN) return bool;
		if (type == Type.REFERENCE) return scope.resolve(target).bool();
			return true;
	}
	
	int num() {
		if (type == Type.NUMBER) return number;
		if (type == Type.STRING) {
			try {
				return Integer.parseInt(text);
			} catch (NumberFormatException e) {
				error("can't convert '" + text + "' to a number");
			}
		}
		error("can't convert '" + str() + "' to a number");
		return -1;
	}
	
	RödaValue resolve(boolean implicite) {
		if (type == Type.REFERENCE) {
			RödaValue t = scope.resolve(target);
			if (t == null) error("variable not found (via " + (implicite ? "implicite" : "explicite") + " reference): " + target);
			return t;
		}
		error("can't dereference a " + type);
		return null;
	}

	RödaValue impliciteResolve() {
		if (isReference()) return resolve(true);
		return this;
	}
	
	void assign(RödaValue value) {
		if (type == Type.REFERENCE) {
			scope.set(target, value);
			return;
		}
		error("can't assign a " + type);
	}
	
	void assignLocal(RödaValue value) {
		if (type == Type.REFERENCE) {
			scope.setLocal(target, value);
			return;
		}
		error("can't assign a " + type);
	}
	
	boolean weakEq(RödaValue value) {
		return str().equals(value.str());
	}

	/** Viittauksien vertaileminen kielletty **/
	boolean halfEq(RödaValue value) {
		if (type == Type.STRING && value.type == Type.NUMBER
		    || type == Type.NUMBER && value.type == Type.STRING) {
			return weakEq(value);
		}
		else return strongEq(value);
	}
		
	/** Viittauksien vertaileminen kielletty **/
	boolean strongEq(RödaValue value) {
		if (type != value.type) return false;
		switch (type) {
		case STRING:
			return text.equals(value.text);
		case NUMBER:
			return number == value.number;
		case BOOLEAN:
			return bool == value.bool;
		case FUNCTION:
			return function == value.function;
		case NATIVE_FUNCTION:
			return nfunction == value.nfunction;
		case LIST:
			boolean ans = true;
			for (int i = 0; i < list.size(); i++)
				ans &= list.get(i).strongEq(value.list.get(i));
			return ans;
		case REFERENCE:
			// tätä ei oikeasti pitäisi koskaan tapahtua
			return false;
		default:
			// eikä tätä
			return false;
		}
	}
	
	boolean isFunction() {
		return type == Type.FUNCTION || type == Type.NATIVE_FUNCTION;
	}
	
	boolean isNativeFunction() {
		return type == Type.NATIVE_FUNCTION;
	}
	
	boolean isList() {
		return type == Type.LIST;
	}

	boolean isNumber() {
		return type == Type.STRING || type == Type.NUMBER;
	}
	
	boolean isString() {
		return type == Type.STRING || type == Type.NUMBER;
	}

	boolean isBoolean() {
		return type == Type.BOOLEAN;
	}
	
	Boolean isReference() {
		return type == Type.REFERENCE;
	}

	String typeString() {
		return type.toString();
	}
	
	@Override
	public String toString() {
			return "RödaValue{str=" + str() + "}";
	}

	static class NativeFunction {
		String name;
		NativeFunctionBody body;
		boolean isVarargs;
		List<Parameter> parameters;
	}
	
	static interface NativeFunctionBody {
		public void exec(List<RödaValue> rawArgs, List<RödaValue> args, RödaScope scope,
				 RödaStream in, RödaStream out);
	}

	public static RödaValue valueFromString(String text) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.STRING;
		val.text = text;
		return val;
	}

	public static RödaValue valueFromInt(int number) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.NUMBER;
		val.number = number;
		return val;
	}

	public static RödaValue valueFromBoolean(boolean bool) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.BOOLEAN;
		val.bool = bool;
		return val;
	}

	public static RödaValue valueFromList(List<RödaValue> list) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.LIST;
		val.list = list;
		return val;
	}

	public static RödaValue valueFromList(RödaValue... elements) {
		return valueFromList(new ArrayList<>(Arrays.asList(elements)));
	}
	
	public static RödaValue valueFromFunction(Function function) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.FUNCTION;
		val.function = function;
		return val;
	}
	
	public static RödaValue valueFromFunction(Function function, RödaScope localScope) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.FUNCTION;
		val.function = function;
		val.scope = localScope;
		return val;
	}

	public static RödaValue valueFromNativeFunction(String name, NativeFunctionBody body, List<Parameter> parameters, boolean isVarargs) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.NATIVE_FUNCTION;
		NativeFunction function = new NativeFunction();
		function.name = name;
		function.body = body;
		function.isVarargs = isVarargs;
		function.parameters = parameters;
		val.nfunction = function;
		return val;
	}

	public static RödaValue valueFromReference(RödaScope scope, String name) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.REFERENCE;
		val.scope = scope;
		val.target = name;
		return val;
	}
}
