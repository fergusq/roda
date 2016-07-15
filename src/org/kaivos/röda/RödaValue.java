package org.kaivos.röda;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.kaivos.röda.type.RödaBoolean;

import static org.kaivos.röda.type.RödaNativeFunction.NativeFunction;
import static org.kaivos.röda.Parser.Function;
import static org.kaivos.röda.Interpreter.RödaScope;
import static org.kaivos.röda.Interpreter.error;

public abstract class RödaValue {

	public static final Datatype STRING = new Datatype("string");
	public static final Datatype NUMBER = new Datatype("number");
	public static final Datatype INTEGER = new Datatype("integer");
	public static final Datatype FLOATING = new Datatype("floating");
	public static final Datatype BOOLEAN = new Datatype("boolean");
	public static final Datatype FLAG = new Datatype("flag");
	public static final Datatype LIST = new Datatype("list");
	public static final Datatype MAP = new Datatype("map");
	public static final Datatype FUNCTION = new Datatype("function");
	public static final Datatype NFUNCTION = new Datatype("nfunction");
	public static final Datatype REFERENCE = new Datatype("reference");

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
	
	public long integer() {
		error("can't convert '" + str() + "' to an integer");
		return -1;
	}
	
	public double floating() {
		error("can't convert '" + str() + "' to a float");
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
		error("a " + typeString() + " doesn't have elements");
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
	
	public RödaValue callOperator(Parser.Expression.CType operator, RödaValue value) {
		switch (operator) {
		case EQ:
			return RödaBoolean.of(this.halfEq(value));
		case NEQ:
			return RödaBoolean.of(!this.halfEq(value));
		case MATCHES:
			if (!this.is(STRING)) error("tried to MATCH a " + this.typeString());
			if (!value.is(STRING)) error("tried to MATCH a " + value.typeString());
			return RödaBoolean.of(this.str().matches(value.str()));
		default:
			error("can't " + operator.name() + " a " + basicIdentity() + " and a " + value.basicIdentity());
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
	
	public final boolean isFlag(String value) {
		return is(FLAG) && str().equals(value);
	}

	public final String typeString() {
		return basicIdentity().toString();
	}
	
	@Override
	public String toString() {
			return "RödaValue{str=" + str() + "}";
	}
}
