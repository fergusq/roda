package org.kaivos.röda;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import static org.kaivos.röda.type.RödaNativeFunction.NativeFunction;
import static org.kaivos.röda.Parser.Function;
import static org.kaivos.röda.Parser.Parameter;
import static org.kaivos.röda.Parser.Datatype;
import static org.kaivos.röda.Interpreter.RödaScope;
import static org.kaivos.röda.Interpreter.error;

public abstract class RödaValue {

	public static final Datatype STRING = new Datatype("string");
	public static final Datatype NUMBER = new Datatype("number");
	public static final Datatype BOOLEAN = new Datatype("boolean");
	public static final Datatype FLAG = new Datatype("flag");
	public static final Datatype LIST = new Datatype("list");
	public static final Datatype MAP = new Datatype("map");
	public static final Datatype FUNCTION = new Datatype("function");
	public static final Datatype NFUNCTION = new Datatype("nfunction");

	protected RödaValue() {} // käytä apufunktioita
	
	public abstract RödaValue copy();
	
	public abstract String str();

	public String target() {
		error("can't cast a " + typeString() + " to a reference");
		return null;
	}

	public RödaScope localScope() {
		error("can't cast a " + typeString() + " to a function");
		return null;
	}
	
	public boolean bool() {
		return true;
	}
	
	public long num() {
		error("can't convert '" + str() + "' to a number");
		return -1;
	}

	public List<RödaValue> list() {
		error("can't cast a " + typeString() + " to a list");
		return null;
	}

	public Map<String, RödaValue> map() {
		error("can't cast a " + typeString() + " to a list");
		return null;
	}

	public Function function() {
		error("can't convert '" + str() + "' to a function");
		return null;
	}

	public NativeFunction nfunction() {
		error("can't convert '" + str() + "' to a function");
		return null;
	}

	public RödaValue get(RödaValue index) {
		error("a " + typeString() + " doesn't have elements");
		return null;
	}

	public void set(RödaValue index, RödaValue value) {
		if (!isList()) error("a " + typeString() + " doesn't have elements");
	}

	public RödaValue contains(RödaValue index) {
		error("a " + typeString() + " doesn't have elements");
		return null;
	}

	public RödaValue containsValue(RödaValue value) {
		error("a " + typeString() + " doesn't have elements");
		return null;
	}

	public RödaValue length() {
		error("a " + typeString() + " doesn't have length");
		return null;
	}

	public RödaValue slice(RödaValue start, RödaValue end) {
		error("a " + typeString() + " doesn't have elements");
		return null;
	}

	public RödaValue join(RödaValue separator) {
		error("can't join a " + typeString());
		return null;
	}

	public void add(RödaValue value) {
	        error("can't add values to a " + typeString());
	}

	public void addAll(List<RödaValue> value) {
	        error("can't add values to a " + typeString());
	}

	public void setField(String field, RödaValue value) {
		error("can't edit a " + typeString());
	}

	public RödaValue getField(String field) {
		error("can't read a " + typeString());
		return null;
	}

	public Map<String, RödaValue> fields() {
		error("a " + typeString() + " doesn't have fields");
		return null;
	}
	
	public RödaValue resolve(boolean implicite) {
	        error("can't dereference a " + typeString());
		return null;
	}
	
	public RödaValue unsafeResolve() {
	        error("can't dereference a " + typeString());
		return null;
	}

	public RödaValue impliciteResolve() {
	        return this;
	}
	
	public void assign(RödaValue value) {
		error("can't assign a " + typeString());
	}
	
	public void assignLocal(RödaValue value) {
		error("can't assign a " + typeString());
	}

	private List<Datatype> identities = new ArrayList<>();

	protected void assumeIdentity(String name) {
		identities.add(new Datatype(name));
	}

	protected void assumeIdentity(Datatype identity) {
		identities.add(identity);
	}

	protected void assumeIdentities(List<Datatype> identities) {
		this.identities.addAll(identities);
	}

	public List<Datatype> identities() {
		return identities;
	}

	public Datatype basicIdentity() {
		return identities.get(0);
	}

	public boolean is(String type) {
		return is(new Datatype(type));
	}
	
	public boolean is(Datatype type) {
	        return identities.contains(type);
	}

	boolean weakEq(RödaValue value) {
		return str().equals(value.str());
	}

	/** Viittauksien vertaileminen kielletty **/
	boolean halfEq(RödaValue value) {
		if (isString() && value.isNumber()
		    || isNumber() && value.isString()) {
			return weakEq(value);
		}
		else return strongEq(value);
	}
		
	/** Viittauksien vertaileminen kielletty **/
	public boolean strongEq(RödaValue value) {
	        return false;
	}
	
	public boolean isFunction() {
		return false;
	}
	
	public boolean isNativeFunction() {
		return false;
	}
	
	public boolean isList() {
		return false;
	}
	
	public boolean isMap() {
		return false;
	}

	public boolean isRecordInstance() {
		return false;
	}

	public boolean isNumber() {
		return false;
	}
	
	public boolean isString() {
		return false;
	}
	
	public boolean isFlag() {
		return false;
	}
	
	public boolean isFlag(String flag) {
		return false;
	}

	public boolean isBoolean() {
		return false;
	}
	
	public boolean isReference() {
		return false;
	}

	public final String typeString() {
		return basicIdentity().toString();
	}
	
	@Override
	public String toString() {
			return "RödaValue{str=" + str() + "}";
	}
}
