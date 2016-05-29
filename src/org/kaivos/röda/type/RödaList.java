package org.kaivos.röda.type;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;

import static java.util.stream.Collectors.joining;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Parser.Datatype;

public class RödaList extends RödaValue {

	private Datatype type;
	private List<RödaValue> list;

	private RödaList(List<RödaValue> list) {
		assumeIdentity(LIST);
		this.type = null;
		this.list = list;
	}

	private RödaList(Datatype type, List<RödaValue> list) {
		if (type != null)
			assumeIdentity(new Datatype(LIST.name, Arrays.asList(type)));
		assumeIdentity("list");
		this.type = type;
		this.list = list;
		if (type != null) {
			for (RödaValue value : list) {
				if (!value.is(type)) {
					error("can't make a " + typeString()
					      + " that contains a " + value.typeString());
				}
			}
		}
	}

	@Override public RödaValue copy() {
		List<RödaValue> newList = new ArrayList<>(list.size());
		for (RödaValue item : list) newList.add(item.copy());
		return new RödaList(type, newList);
	}

	@Override public String str() {
		return "[" + list.stream().map(RödaValue::str).collect(joining(", ")) + "]";
	}

	@Override public List<RödaValue> list() {
		return Collections.unmodifiableList(list);
	}

	@Override public RödaValue get(RödaValue indexVal) {
		long index = indexVal.num();
		if (index < 0) index = list.size()+index;
		if (list.size() <= index) error("array index out of bounds: index " + index
						+ ", size " + list.size());
		if (index > Integer.MAX_VALUE) error("array index out of bounds: too large index: "+index);
		return list.get((int) index);
	}

	@Override public void set(RödaValue indexVal, RödaValue value) {
		long index = indexVal.num();
		if (index < 0) index = list.size()+index;
		if (list.size() <= index)
			error("array index out of bounds: index " + index
			      + ", size " + list.size());
		if (type != null && !value.is(type))
			error("cannot put a " + value.typeString() + " to a " + typeString());
		if (index > Integer.MAX_VALUE) error("array index out of bounds: too large index: "+index);
		list.set((int) index, value);
	}

	@Override public RödaValue contains(RödaValue indexVal) {
		long index = indexVal.num();
		if (index < 0) index = list.size()+index;
		return RödaBoolean.of(index < list.size());
	}

	@Override public RödaValue containsValue(RödaValue value) {
		for (RödaValue element : list) {
			if (element.strongEq(value)) {
				return RödaBoolean.of(true);
			}
		}
		return RödaBoolean.of(false);
	}

	@Override public RödaValue length() {
		return RödaNumber.of(list.size());
	}

	@Override public RödaValue slice(RödaValue startVal, RödaValue endVal) {
		long start = startVal == null ? 0 : startVal.num();
		long end = endVal == null ? list.size() : endVal.num();
		if (start < 0) start = list.size()+start;
		if (end < 0) end = list.size()+end;
		if (end == 0 && start > 0) end = list.size();
		if (start > Integer.MAX_VALUE) error("array index out of bounds: too large index: "+start);
		if (end > Integer.MAX_VALUE) error("array index out of bounds: too large index: "+end);
		return of(list.subList((int) start, (int) end));
	}

	@Override public RödaValue join(RödaValue separatorVal) {
		String separator = separatorVal.str();
		String text = "";
		int i = 0; for (RödaValue val : list) {
			if (i++ != 0) text += separator;
			text += val.str();
		}
		return RödaString.of(text);
	}

	@Override public void add(RödaValue value) {
		if (type != null && !value.is(type))
			error("cannot put a " + value.typeString() + " to a " + typeString());
		list.add(value);
	}

	@Override public void addAll(List<RödaValue> values) {
		if (type != null) {
			for (RödaValue value : values) {
				if (!value.is(type))
					error("cannot put a " + value.typeString() + " to a " + typeString());
			}
		}
		list.addAll(values);
	}

	@Override public boolean isList() {
		return true;
	}

	@Override public boolean strongEq(RödaValue value) {
		if (!value.isList()) return false;
		if (list.size() != value.list().size()) return false;
		boolean ans = true;
		for (int i = 0; i < list.size(); i++)
			ans &= list.get(i).strongEq(value.list().get(i));
		return ans;
	}

	public static RödaList of(List<RödaValue> list) {
		return new RödaList(new ArrayList<>(list));
	}

	public static RödaList of(Datatype type, List<RödaValue> list) {
		return new RödaList(type, new ArrayList<>(list));
	}

	public static RödaList of(String type, List<RödaValue> list) {
		return new RödaList(new Datatype(type), new ArrayList<>(list));
	}

	public static RödaList of(RödaValue... elements) {
		return new RödaList(new ArrayList<>(Arrays.asList(elements)));
	}

	public static RödaList empty() {
		return new RödaList(new ArrayList<>());
	}

	public static RödaList empty(Datatype type) {
		return new RödaList(type, new ArrayList<>());
	}
}
