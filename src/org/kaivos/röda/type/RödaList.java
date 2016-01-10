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
		assumeIdentity("list");
		this.type = null;
		this.list = list;
	}

	private RödaList(Datatype type) {
		assumeIdentity(new Datatype("list", Arrays.asList(type)));
		this.type = type;
		this.list = new ArrayList<>();
	}

	@Override public RödaValue copy() {
		List<RödaValue> newList = new ArrayList<>(list.size());
		for (RödaValue item : list) newList.add(item.copy());
		return new RödaList(newList);
	}

	@Override public String str() {
		return "(" + list.stream().map(RödaValue::str).collect(joining(" ")) + ")";
	}

	@Override public List<RödaValue> list() {
		return Collections.unmodifiableList(list);
	}

	@Override public RödaValue get(RödaValue indexVal) {
		int index = indexVal.num();
		if (index < 0) index = list.size()+index;
		if (list.size() <= index) error("array index out of bounds: index " + index
						+ ", size " + list.size());
		return list.get(index);
	}

	@Override public void set(RödaValue indexVal, RödaValue value) {
		int index = indexVal.num();
		if (index < 0) index = list.size()+index;
		if (list.size() <= index)
			error("array index out of bounds: index " + index
			      + ", size " + list.size());
		if (type != null && !value.is(type))
			error("cannot put a " + value.typeString() + " to a " + typeString());
		list.set(index, value);
	}

	@Override public RödaValue contains(RödaValue indexVal) {
		int index = indexVal.num();
		if (index < 0) index = list.size()+index;
		return RödaBoolean.of(index < list.size());
	}

	@Override public RödaValue length() {
		return RödaNumber.of(list.size());
	}

	@Override public RödaValue slice(RödaValue startVal, RödaValue endVal) {
		int start = startVal == null ? 0 : startVal.num();
		int end = endVal == null ? list.size() : endVal.num();
		if (start < 0) start = list.size()+start;
		if (end < 0) end = list.size()+end;
		if (end == 0 && start > 0) end = list.size();
		return of(list.subList(start, end));
	}

	@Override public RödaValue join(RödaValue separatorVal) {
		String separator = separatorVal.str();
		String text = "";
		int i = 0; for (RödaValue val : list) {
			if (i++ != 0) text += separator;
			text += val.str();
		}
		return valueFromString(text);
	}

	@Override public void add(RödaValue value) {
		if (type != null && !value.is(type))
			error("cannot put a " + value.typeString() + " to a " + typeString());
		list.add(value);
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

	public static RödaList of(RödaValue... elements) {
		return new RödaList(new ArrayList<>(Arrays.asList(elements)));
	}

	public static RödaList empty() {
		return new RödaList(new ArrayList<>());
	}

	public static RödaList empty(Datatype type) {
		return new RödaList(type);
	}
}
