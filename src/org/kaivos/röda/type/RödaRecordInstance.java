package org.kaivos.röda.type;

import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Interpreter.illegalArguments;
import static org.kaivos.röda.Interpreter.typeMismatch;
import static org.kaivos.röda.Interpreter.unknownName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Datatype;
import org.kaivos.röda.runtime.Record;

public class RödaRecordInstance extends RödaValue {
	private boolean isValueType;
	private Map<String, RödaValue> fields;
	private Map<String, Datatype> fieldTypes;

	private RödaRecordInstance(List<Datatype> identities,
				   boolean isValueType,
				   Map<String, RödaValue> fields,
				   Map<String, Datatype> fieldTypes) {
		assumeIdentities(identities);
		this.isValueType = isValueType;
		this.fields = fields;
		this.fieldTypes = fieldTypes;
	}

	@Override public RödaValue copy() {
		if (isValueType) {
			Map<String, RödaValue> newFields = new HashMap<>();
			for (Map.Entry<String, RödaValue> item : fields.entrySet())
				newFields.put(item.getKey(), item.getValue().copy());
			return new RödaRecordInstance(identities(),
						      true,
						      newFields,
						      fieldTypes);
		} else {
			return this;
		}
	}

	@Override public String str() {
		return "<" + typeString() + " instance " + super.hashCode() + ">";
	}

	@Override public void setField(String field, RödaValue value) {
		if (fieldTypes.get(field) == null)
			unknownName(typeString() + " doesn't have field '" + field + "'");
		if (!value.is(fieldTypes.get(field)))
			typeMismatch("can't put " + value.typeString()
			      + " to " + fieldTypes.get(field) + " field");
		this.fields.put(field, value);
	}

	@Override public RödaValue getField(String field) {
		if (fieldTypes.get(field) == null)
			unknownName(typeString() + " doesn't have field '" + field + "'");
		RödaValue a = fields.get(field);
		if (a == null)
			error("field '" + field + "' hasn't been initialized");
		return a;
	}

	@Override public boolean strongEq(RödaValue value) {
		if (!basicIdentity().equals(value.basicIdentity()))
			return false;
		boolean ans = true;
		for (Map.Entry<String, RödaValue> entry : fields.entrySet())
			ans &= entry.getValue().strongEq(value.fields().get(entry.getKey()));
		return ans;
	}
	
	@Override public Map<String, RödaValue> fields() {
		return Collections.unmodifiableMap(fields);
	}
	
	@Override
	public int hashCode() {
		return basicIdentity().hashCode() + fields.hashCode();
	}

	public static RödaRecordInstance of(Record record, List<Datatype> typearguments) {
		Map<String, Datatype> fieldTypes = new HashMap<>();
		List<Datatype> identities = new ArrayList<>();
		construct(record, typearguments, fieldTypes, identities);
		return new RödaRecordInstance(identities, record.isValueType, new HashMap<>(), fieldTypes);
	}

	private static void construct(Record record, List<Datatype> typearguments,
				      Map<String, Datatype> fieldTypes,
				      List<Datatype> identities) {
		identities.add(new Datatype(record.name, typearguments, record.declarationScope));
		for (Record.Field field : record.fields) {
			// TODO check double inheritance
			fieldTypes.put(field.name, substitute(field.type, record.typeparams, typearguments));
		}
		for (Record.SuperExpression superExp : record.superTypes) {
			Datatype superType = substitute(superExp.type, record.typeparams, typearguments);
			Record r = superType.resolve();
			if (r == null)
				unknownName("super type " + superType.name + " not found");
			construct(r, superType.subtypes, fieldTypes, identities);
		}
	}

	private static Datatype substitute(Datatype type, List<String> typeparams, List<Datatype> typeargs) {
		if (typeparams.size() != typeargs.size())
			illegalArguments("wrong number of typearguments");
		if (typeparams.contains(type.name)) {
			if (!type.subtypes.isEmpty())
				error("a typeparameter can't have subtypes");
			return typeargs.get(typeparams.indexOf(type.name));
		}
		List<Datatype> subtypes = new ArrayList<>();
		for (Datatype t : type.subtypes) {
			subtypes.add(substitute(t, typeparams, typeargs));
		}
		return new Datatype(type.name, subtypes, type.scope);
	}
}
