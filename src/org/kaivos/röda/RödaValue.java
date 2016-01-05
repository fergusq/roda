package org.kaivos.röda;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import static java.util.stream.Collectors.joining;

import org.kaivos.röda.RödaStream;
import static org.kaivos.röda.RödaStream.StreamType;
import static org.kaivos.röda.RödaStream.ValueStream;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Interpreter.RödaScope;
import static org.kaivos.röda.Parser.Function;
import static org.kaivos.röda.Parser.Parameter;
import static org.kaivos.röda.Parser.Record;
import static org.kaivos.röda.Parser.Datatype;

public class RödaValue {
	private enum Type {
		FUNCTION,
		NATIVE_FUNCTION,
		STRING,
		NUMBER,
		BOOLEAN,
		REFERENCE,
		LIST,
		RECORD_INSTANCE
	}
	
	private Type type;
	
	// STRING
	private String text;

	// INT
	private int number;

	// BOOLEAN
	private boolean bool;
	
	// FUNCTION
	Function function;
	
	// NATIVE_FUNCTION
	NativeFunction nfunction;
	
	// REFERENCE
	String target;
	RödaScope scope;
	
	// LIST
	private List<RödaValue> list;

	// RECORD_INSTANCE
	private Record record;
	private Map<String, RödaValue> fields;
	private Map<String, Datatype> fieldTypes;

	// LIST & RECORD_INSTANCE
	private List<Datatype> typearguments = new ArrayList<>();
	
	private RödaValue() {} // käytä apufunktioita
	
	public RödaValue copy() {
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
			for (RödaValue item : list) val.list.add(item.copy());
		} else val.list = null;
		val.record = record;
		if (fields != null) {
			val.fields = new HashMap<>();
			for (Map.Entry<String, RödaValue> item : fields.entrySet())
				val.fields.put(item.getKey(), item.getValue().copy());
		} else val.fields = null;
		val.fieldTypes = fieldTypes;
		val.typearguments = typearguments;
		return val;
	}
	
	public String str() {
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
		if (type == Type.RECORD_INSTANCE) {
			return "<a " + typeString() + " instance>";
		}
		error("unknown type " + type);
		return null;
	}
	
	public boolean bool() {
		if (type == Type.BOOLEAN) return bool;
		if (type == Type.REFERENCE) return scope.resolve(target).bool();
			return true;
	}
	
	public int num() {
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

	public List<RödaValue> list() {
		if (!isList()) error("can't cast a " + typeString() + " to a list");
		return Collections.unmodifiableList(list);
	}

	public RödaValue get(int index) {
		if (!isList()) error("a " + typeString() + " doesn't have elements");
		if (index < 0) index = list.size()+index;
		if (list.size() <= index) error("array index out of bounds: index " + index + ", size " + list.size());
		return list.get(index);
	}

	public void set(int index, RödaValue value) {
		if (!isList()) error("a " + typeString() + " doesn't have elements");
		if (!typearguments.isEmpty()
		    && !value.is(typearguments.get(0)))
			error("can't add a " + typeString() + " to a " + typeString());
		if (index < 0) index = list.size()+index;
		if (list.size() <= index)
			error("array index out of bounds: index " + index
			      + ", size " + list.size());
		list.set(index, value);
	}

	public int length() {
		if (!isList() && !isString()) error("a " + typeString() + " doesn't have length");
		if (isList()) return list.size();
		else return text.length();
	}

	public RödaValue slice(int start, int end) {
		if (!isList()) error("a " + typeString() + " doesn't have elements");
		if (start < 0) start = list.size()+start;
		if (end < 0) end = list.size()+end;
		if (end == 0 && start > 0) end = list.size();
		
		return valueFromList(list.subList(start, end));
	}

	public RödaValue join(String separator) {
		if (!isList()) error("can't join a " + typeString());
		String text = "";
		int i = 0; for (RödaValue val : list) {
			if (i++ != 0) text += separator;
			text += val.str();
		}
		return valueFromString(text);
	}

	public void add(RödaValue value) {
		if (!isList()) error("can't add values to a " + typeString());
		if (!typearguments.isEmpty()
		    && !value.is(typearguments.get(0)))
			error("can't add a " + typeString() + " to a " + typeString());
		this.list.add(value);
	}

	public void setField(String field, RödaValue value) {
		if (!isRecordInstance()) error("can't edit a " + typeString());
		if (fieldTypes.get(field) == null) error("a " + typeString() + " doesn't have field '" + field + "'");
		if (!value.is(fieldTypes.get(field))) error("can't put a " + value.typeString()
							    + " in a " + fieldTypes.get(field) + " field");
		this.fields.put(field, value);
	}

	public RödaValue getField(String field) {
		if (!isRecordInstance()) error("can't edit a " + typeString());
		if (fieldTypes.get(field) == null) error("a " + typeString() + " doesn't have field '" + field + "'");
		return fields.get(field);
	}
	
	public RödaValue resolve(boolean implicite) {
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

	public boolean is(Datatype type) {
		if (typearguments != null && !typearguments.equals(type.subtypes))
			return false;

		if (this.type == Type.RECORD_INSTANCE)
			return record.name.equals(type.name);

	        switch (this.type) {
		case STRING:
			return type.name.equals("string");
		case NUMBER:
			return type.name.equals("number");
		case BOOLEAN:
			return type.name.equals("boolean");
		case FUNCTION:
			return type.name.equals("function");
		case NATIVE_FUNCTION:
			return type.name.equals("function");
		case LIST:
			return type.name.equals("list");
		case REFERENCE:
			// tätä ei oikeasti pitäisi koskaan tapahtua
			return false;
		default:
			// eikä tätä
			return false;
		}
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
		case LIST: {
			boolean ans = true;
			for (int i = 0; i < list.size(); i++)
				ans &= list.get(i).strongEq(value.list.get(i));
			return ans;
		}
		case RECORD_INSTANCE: {
			boolean ans = true;
			for (Map.Entry<String, RödaValue> entry : fields.entrySet())
				ans &= entry.getValue().strongEq(value.fields.get(entry.getKey()));
			return ans;
		}	
		case REFERENCE:
			// tätä ei oikeasti pitäisi koskaan tapahtua
			return false;
		default:
			// eikä tätä
			return false;
		}
	}
	
	public boolean isFunction() {
		return type == Type.FUNCTION || type == Type.NATIVE_FUNCTION;
	}
	
	public boolean isNativeFunction() {
		return type == Type.NATIVE_FUNCTION;
	}
	
	public boolean isList() {
		return type == Type.LIST;
	}

	public boolean isRecordInstance() {
		return type == Type.RECORD_INSTANCE;
	}

	public boolean isNumber() {
		return type == Type.STRING || type == Type.NUMBER;
	}
	
	public boolean isString() {
		return type == Type.STRING || type == Type.NUMBER;
	}

	public boolean isBoolean() {
		return type == Type.BOOLEAN;
	}
	
	public Boolean isReference() {
		return type == Type.REFERENCE;
	}

	public String typeString() {
		if (type == Type.RECORD_INSTANCE) {
			if (typearguments.isEmpty()) {
				return record.name;
			}
			else {
				return record.name + "<" + typearguments.stream()
					.map(Datatype::toString)
					.collect(joining(", ")) + ">";
			}
		}
		if (type == Type.LIST
		    && !typearguments.isEmpty()) {
			return "list<" + typearguments.stream()
				.map(Datatype::toString)
				.collect(joining(", ")) + ">";
		}
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
		StreamType input;
		StreamType output;
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

	public static RödaValue valueFromNativeFunction(String name, NativeFunctionBody body,
							List<Parameter> parameters, boolean isVarargs) {
		return valueFromNativeFunction(name, body, parameters, isVarargs,
					       new ValueStream(), new ValueStream());
	}

	public static RödaValue valueFromNativeFunction(String name, NativeFunctionBody body,
							List<Parameter> parameters, boolean isVarargs,
							StreamType input, StreamType output) {
		RödaValue val = new RödaValue();
		val.type = RödaValue.Type.NATIVE_FUNCTION;
		NativeFunction function = new NativeFunction();
		function.name = name;
		function.body = body;
		function.isVarargs = isVarargs;
		function.parameters = parameters;
		function.input = input;
		function.output = output;
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
