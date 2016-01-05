package org.kaivos.röda.type;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Parser.Record;
import static org.kaivos.röda.Parser.Datatype;

public class RödaRecordInstance extends RödaValue {
	private Record record;
	private Map<String, RödaValue> fields;
	private Map<String, Datatype> fieldTypes;

	private RödaRecordInstance(List<Datatype> identities,
				   Record record,
				   Map<String, RödaValue> fields,
				   Map<String, Datatype> fieldTypes) {
		assumeIdentities(identities);
		this.record = record;
		this.fields = fields;
		this.fieldTypes = fieldTypes;
	}

	@Override public RödaValue copy() {
		Map<String, RödaValue> newFields = new HashMap<>();
		for (Map.Entry<String, RödaValue> item : fields.entrySet())
			newFields.put(item.getKey(), item.getValue().copy());
		return new RödaRecordInstance(identities(),
					      record,
					      newFields,
					      fieldTypes);
	}

	@Override public String str() {
		return "<a " + typeString() + " instance>";
	}

	@Override public void setField(String field, RödaValue value) {
		if (fieldTypes.get(field) == null)
			error("a " + typeString() + " doesn't have field '" + field + "'");
		if (!value.is(fieldTypes.get(field)))
			error("can't put a " + value.typeString()
			      + " in a " + fieldTypes.get(field) + " field");
		this.fields.put(field, value);
	}

	@Override public RödaValue getField(String field) {
		if (fieldTypes.get(field) == null)
			error("a " + typeString() + " doesn't have field '" + field + "'");
		return fields.get(field);
	}

	@Override public boolean isRecordInstance() {
		return true;
	}

	@Override public String typeString() {
		return basicIdentity().toString();
	}

	@Override public boolean strongEq(RödaValue value) {
		if (!basicIdentity().equals(value.basicIdentity()))
			return false;
		boolean ans = true;
		for (Map.Entry<String, RödaValue> entry : fields.entrySet())
			ans &= entry.getValue().strongEq(value.fields().get(entry.getKey()));
		return ans;
	}
}
