package org.kaivos.röda.type;

import static org.kaivos.röda.Interpreter.outOfBounds;
import static org.kaivos.röda.Interpreter.typeMismatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Datatype;

public class RödaMap extends RödaValue {

	private Datatype type;
	private Map<String, RödaValue> map;

	private RödaMap(Map<String, RödaValue> map) {
		assumeIdentity(MAP);
		this.type = null;
		this.map = map;
	}

	private RödaMap(Datatype type, Map<String, RödaValue> map) {
		if (type != null)
			assumeIdentity(new Datatype(MAP.name, Arrays.asList(type)));
		assumeIdentity("map");
		this.type = type;
		this.map = map;
		if (type != null) {
			for (RödaValue value : map.values()) {
				if (!value.is(type)) {
					typeMismatch("can't make a " + typeString()
					      + " that contains a " + value.typeString());
				}
			}
		}
	}

	@Override public RödaValue copy() {
		Map<String, RödaValue> newMap = new HashMap<>(map.size());
		for (Map.Entry<String, RödaValue> item : map.entrySet())
			newMap.put(item.getKey(), item.getValue().copy());
		return new RödaMap(type, newMap);
	}

	@Override public String str() {
		return "<map instance "+super.hashCode()+">";
	}

	@Override public Map<String, RödaValue> map() {
		return Collections.unmodifiableMap(map);
	}

	@Override public RödaValue get(RödaValue indexVal) {
		String index = indexVal.str();
		if (!map.containsKey(index)) outOfBounds("key does not exist: " + index);
		return map.get(index);
	}

	@Override public void set(RödaValue indexVal, RödaValue value) {
		String index = indexVal.str();
		if (type != null && !value.is(type))
			typeMismatch("cannot put " + value.typeString() + " to " + typeString());
		map.put(index, value);
	}
	
	@Override public void del(RödaValue indexVal) {
		String index = indexVal.str();
		map.remove(index);
	}

	@Override public RödaValue contains(RödaValue indexVal) {
		String index = indexVal.str();
		return RödaBoolean.of(map.containsKey(index));
	}

	@Override public RödaValue length() {
		return RödaInteger.of(map.size());
	}

	@Override public boolean strongEq(RödaValue value) {
		if (!value.is(MAP)) return false;
		if (map.size() != value.map().size()) return false;
		boolean ans = true;
		for (int i = 0; i < map.size(); i++)
			ans &= map.get(i).strongEq(value.map().get(i));
		return ans;
	}
	
	@Override
	public int hashCode() {
		return map.hashCode();
	}

	public static RödaMap of(Map<String, RödaValue> map) {
		return new RödaMap(new HashMap<>(map));
	}

	public static RödaMap of(Datatype type, Map<String, RödaValue> map) {
		return new RödaMap(type, new HashMap<>(map));
	}

	public static RödaMap of(String type, Map<String, RödaValue> map) {
		return new RödaMap(new Datatype(type), new HashMap<>(map));
	}

	public static RödaMap empty() {
		return new RödaMap(new HashMap<>());
	}

	public static RödaMap empty(Datatype type) {
		return new RödaMap(type, new HashMap<>());
	}
}
