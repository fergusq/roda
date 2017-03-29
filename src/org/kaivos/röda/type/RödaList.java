package org.kaivos.röda.type;

import static java.util.stream.Collectors.joining;
import static org.kaivos.röda.Interpreter.outOfBounds;
import static org.kaivos.röda.Interpreter.typeMismatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.kaivos.röda.RödaValue;
import org.kaivos.röda.Parser.ExpressionTree.CType;
import org.kaivos.röda.runtime.Datatype;

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
					typeMismatch(typeString()
					      + " can't contain a value of type " + value.typeString());
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

	@Override public List<RödaValue> modifiableList() {
		return list;
	}
	
	private void checkInRange(long index, boolean allowOneAfterLast) {
		if (list.size() + (allowOneAfterLast ? 1 : 0) <= index)
			outOfBounds("list index out of bounds: index " + index
			      + ", size " + list.size());
		if (index > Integer.MAX_VALUE) outOfBounds("list index out of bounds: too large index: "+index);
		if (index < 0) outOfBounds("list index out of bounds: too small index: "+index);
	}

	@Override public RödaValue get(RödaValue indexVal) {
		long index = indexVal.integer();
		if (index < 0) index = list.size()+index;
		checkInRange(index, false);
		return list.get((int) index);
	}

	@Override public void set(RödaValue indexVal, RödaValue value) {
		long index = indexVal.integer();
		if (index < 0) index = list.size()+index;
		checkInRange(index, false);
		if (type != null && !value.is(type))
			typeMismatch("cannot put " + value.typeString() + " to " + typeString());
		list.set((int) index, value);
	}
	
	private int sliceStart(long step, RödaValue startVal) {
		long start = startVal != null ? startVal.integer() : step > 0 ? 0 : -1;
		if (start < 0) start = list.size()+start;
		checkInRange(start, true);
		return (int) start;
	}
	
	private int sliceEnd(long step, int start, RödaValue endVal) {
		if (endVal == null && step < 0) return -1;
		long end = endVal != null ? endVal.integer() : list.size();
		if (end < 0) end = list.size()+end;
		if (step > 0 && end == 0 && start > 0) end = list.size();
		checkInRange(end, true);
		return (int) end;
	}
	
	private long sliceStep(RödaValue stepVal) {
		long step = stepVal == null ? 1 : stepVal.integer();
		return step;
	}

	@Override public void setSlice(RödaValue startVal, RödaValue endVal, RödaValue stepVal, RödaValue value) {
		long step = sliceStep(stepVal);
		int start = sliceStart(step, startVal);
		int end = sliceEnd(step, start, endVal);
		List<RödaValue> sublist = value.list();
		if (step == 1) {
			for (int i = start; i < end; i++) list.remove(start);
			list.addAll(start, sublist);
		}
		else if (step == -1) {
			for (int i = start; i > end; i--) list.remove(end+1);
			sublist = new ArrayList<>(sublist);
			Collections.reverse(sublist);
			list.addAll(end+1, sublist);
		}
		else if (step > 0) {
			for (int i = start, j = 0; i < end; i += step, j++) list.set(i, sublist.get(j));
		}
		else if (step < 0) {
			for (int i = start, j = 0; i > end; i += step, j++) list.set(i, sublist.get(j));
		}
	}

	@Override public RödaValue slice(RödaValue startVal, RödaValue endVal, RödaValue stepVal) {
		long step = sliceStep(stepVal);
		int start = sliceStart(step, startVal);
		int end = sliceEnd(step, start, endVal);
		if (step == 1)
			return of(list.subList((int) start, (int) end));
		List<RödaValue> newList = new ArrayList<>();
		if (step > 0) {
			for (int i = start; i < end; i += step) newList.add(list.get(i));
		}
		else if (step < 0) {
			for (int i = start; i > end; i += step) newList.add(list.get(i));
		}
		return of(newList);
	}

	@Override public void del(RödaValue indexVal) {
		long index = indexVal.integer();
		if (index < 0) index = list.size()+index;
		checkInRange(index, false);
		list.remove((int) index);
	}

	@Override public void delSlice(RödaValue startVal, RödaValue endVal, RödaValue stepVal) {
		long step = sliceStep(stepVal);
		int start = sliceStart(step, startVal);
		int end = sliceEnd(step, start, endVal);
		if (step > 0) {
			for (int i = start; i < end; i += step-1, end--) list.remove(i);
		}
		else if (step < 0) {
			for (int i = start; i > end; i += step) list.remove(i);
		}
	}

	@Override public RödaValue contains(RödaValue indexVal) {
		long index = indexVal.integer();
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
		return RödaInteger.of(list.size());
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
			typeMismatch("cannot put " + value.typeString() + " to " + typeString());
		list.add(value);
	}

	@Override public void addAll(List<RödaValue> values) {
		if (type != null) {
			for (RödaValue value : values) {
				if (!value.is(type))
					typeMismatch("cannot put " + value.typeString() + " to " + typeString());
			}
		}
		list.addAll(values);
	}

	@Override public void remove(RödaValue value) {
		if (type != null && !value.is(type))
			typeMismatch(typeString() + " can not contain " + value.typeString());
		list.remove(value);
	}

	@Override public boolean strongEq(RödaValue value) {
		if (!value.is(LIST)) return false;
		if (list.size() != value.list().size()) return false;
		boolean ans = true;
		for (int i = 0; i < list.size(); i++)
			ans &= list.get(i).strongEq(value.list().get(i));
		return ans;
	}
	
	private int compare(RödaList other) {
		List<RödaValue> list2 = other.list();
		for (int i = 0; i < Math.min(list.size(), list2.size()); i++) {
			RödaValue val1 = list.get(i);
			RödaValue val2 = list2.get(i);
			if (val1.callOperator(CType.LT, val2).bool()) return -1;
			if (val2.callOperator(CType.LT, val1).bool()) return 1;
		}
		if (list.size() < list2.size()) return -1;
		if (list.size() > list2.size()) return 1;
		return 0;
	}
	
	@Override
	public RödaValue callOperator(CType operator, RödaValue value) {
		switch (operator) {
		case MUL:
			if (!value.is(INTEGER))
				typeMismatch("can't " + operator.name() + " " + typeString() + " and " + value.typeString());
			break;
		case LT:
		case GT:
		case LE:
		case GE:
			if (!value.is(LIST))
				typeMismatch("can't " + operator.name() + " " + typeString() + " and " + value.typeString());
			break;
		default:
		}
		
			
		switch (operator) {
		case MUL: {
			List<RödaValue> newList = new ArrayList<>();
			for (int i = 0; i < value.integer(); i++) {
				newList.addAll(this.list);
			}
			return of(newList);
		}
		case ADD: {
			List<RödaValue> newList = new ArrayList<>(this.list);
			newList.add(value);
			return of(newList);
		}
		case SUB: {
			List<RödaValue> newList = new ArrayList<>(this.list);
			newList.remove(value);
			return of(newList);
		}
		case LT:
			return RödaBoolean.of(compare((RödaList) value) < 0);
		case GT:
			return RödaBoolean.of(compare((RödaList) value) > 0);
		case LE:
			return RödaBoolean.of(compare((RödaList) value) <= 0);
		case GE:
			return RödaBoolean.of(compare((RödaList) value) >= 0);
		default:
			return super.callOperator(operator, value);
		}
	}
	
	@Override
	public int hashCode() {
		return list.hashCode();
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
