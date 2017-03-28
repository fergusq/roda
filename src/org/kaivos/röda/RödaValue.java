package org.kaivos.röda;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

import org.kaivos.röda.runtime.Datatype;
import org.kaivos.röda.runtime.Function;
import org.kaivos.röda.type.RödaBoolean;

import static org.kaivos.röda.type.RödaNativeFunction.NativeFunction;
import static org.kaivos.röda.Interpreter.RödaScope;
import static org.kaivos.röda.Interpreter.typeMismatch;

public abstract class RödaValue {

	public static final Datatype STRING = new Datatype("string");
	public static final Datatype NUMBER = new Datatype("number");
	public static final Datatype INTEGER = new Datatype("integer");
	public static final Datatype FLOATING = new Datatype("floating");
	public static final Datatype BOOLEAN = new Datatype("boolean");
	public static final Datatype LIST = new Datatype("list");
	public static final Datatype MAP = new Datatype("map");
	public static final Datatype FUNCTION = new Datatype("function");
	public static final Datatype NFUNCTION = new Datatype("nfunction");
	public static final Datatype NAMESPACE = new Datatype("namespace");
	public static final Datatype REFERENCE = new Datatype("reference");

	protected RödaValue() {} // käytä apufunktioita
	
	public abstract RödaValue copy();
	
	public abstract String str();
	
	public Pattern pattern() {
		return Pattern.compile(str());
	}

	public String target() {
		typeMismatch("can't cast " + typeString() + " to reference");
		return null;
	}

	public RödaScope localScope() {
		typeMismatch("can't cast " + typeString() + " to function");
		return null;
	}
	
	public boolean bool() {
		return true;
	}
	
	public long integer() {
		typeMismatch("can't cast " + typeString() + " to integer");
		return -1;
	}
	
	public double floating() {
		typeMismatch("can't cast " + typeString() + " to floating");
		return -1;
	}

	public List<RödaValue> list() {
		typeMismatch("can't cast " + typeString() + " to list");
		return null;
	}

	public List<RödaValue> modifiableList() {
		typeMismatch("can't cast " + typeString() + " to list");
		return null;
	}

	public Map<String, RödaValue> map() {
		typeMismatch("can't cast " + typeString() + " to list");
		return null;
	}

	public RödaScope scope() {
		typeMismatch("can't cast " + typeString() + " to namespace");
		return null;
	}

	public Function function() {
		typeMismatch("can't cast " + typeString() + " to function");
		return null;
	}

	public NativeFunction nfunction() {
		typeMismatch("can't cast " + typeString() + " to function");
		return null;
	}

	public RödaValue get(RödaValue index) {
		typeMismatch(typeString() + " doesn't have elements");
		return null;
	}

	public void set(RödaValue index, RödaValue value) {
		typeMismatch(typeString() + " doesn't have elements");
	}

	public void setSlice(RödaValue start, RödaValue end, RödaValue step, RödaValue value) {
		typeMismatch(typeString() + " doesn't have elements");
	}

	public void del(RödaValue index) {
		typeMismatch(typeString() + " doesn't have elements");
	}

	public void delSlice(RödaValue start, RödaValue end, RödaValue step) {
		typeMismatch(typeString() + " doesn't have elements");
	}

	public RödaValue contains(RödaValue index) {
		typeMismatch(typeString() + " doesn't have elements");
		return null;
	}

	public RödaValue containsValue(RödaValue value) {
		typeMismatch(typeString() + " doesn't have elements");
		return null;
	}

	public RödaValue length() {
		typeMismatch(typeString() + " doesn't have length");
		return null;
	}

	public RödaValue slice(RödaValue start, RödaValue end, RödaValue step) {
		typeMismatch(typeString() + " doesn't have elements");
		return null;
	}

	public RödaValue join(RödaValue separator) {
		typeMismatch("can't join " + typeString());
		return null;
	}

	public void add(RödaValue value) {
		typeMismatch("can't add values to " + typeString());
	}

	public void addAll(List<RödaValue> value) {
		typeMismatch("can't add values to " + typeString());
	}

	public void remove(RödaValue value) {
		typeMismatch("can't remove values from " + typeString());
	}

	public void setField(String field, RödaValue value) {
		typeMismatch(typeString() + " doesn't have fields");
	}

	public RödaValue getField(String field) {
		typeMismatch(typeString() + " doesn't have fields");
		return null;
	}

	public Map<String, RödaValue> fields() {
		typeMismatch(typeString() + " doesn't have fields");
		return null;
	}

	public RödaValue resolve(boolean implicite) {
		typeMismatch("can't cast " + typeString() + " to reference");
		return null;
	}

	public RödaValue unsafeResolve() {
		typeMismatch("can't cast " + typeString() + " to reference");
		return null;
	}

	public RödaValue impliciteResolve() {
	        return this;
	}
	
	public void assign(RödaValue value) {
		typeMismatch("can't cast " + typeString() + " to reference");
	}
	
	public void assignLocal(RödaValue value) {
		typeMismatch("can't cast " + typeString() + " to reference");
	}
	
	public RödaValue callOperator(Parser.ExpressionTree.CType operator, RödaValue value) {
		switch (operator) {
		case EQ:
			return RödaBoolean.of(this.halfEq(value));
		case NEQ:
			return RödaBoolean.of(!this.halfEq(value));
		default:
			if (value == null)
				typeMismatch("can't " + operator.name() + " " + basicIdentity());
			else
				typeMismatch("can't " + operator.name() + " " + basicIdentity() + " and " + value.basicIdentity());
			return null;
		}
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
		if (is(STRING) && value.is(INTEGER)
		    || is(INTEGER) && value.is(STRING)) {
			return weakEq(value);
		}
		else return strongEq(value);
	}
		
	/** Viittauksien vertaileminen kielletty **/
	public boolean strongEq(RödaValue value) {
	        return false;
	}

	public final String typeString() {
		return basicIdentity().toString();
	}
	
	@Override
	public String toString() {
			return "RödaValue{str=" + str() + "}";
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof RödaValue) {
			if (!((RödaValue) obj).is(REFERENCE)) {
				return strongEq((RödaValue) obj);
			} else if (is(REFERENCE)) {
				RödaValue target = unsafeResolve();
				return target.equals(((RödaValue) obj).unsafeResolve());
			}
			else return false;
		}
		return super.equals(obj);
	}
}
