package org.kaivos.röda.type;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Arrays;

import static java.util.stream.Collectors.joining;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Parser.Datatype;

public class RödaMap extends RödaValue {

	private Datatype type;
	private Map<String, RödaValue> map;

	private RödaMap(Map<String, RödaValue> map) {
		assumeIdentity("map");
		this.type = null;
		this.map = map;
	}

	private RödaMap(Datatype type) {
		assumeIdentity(new Datatype("map", Arrays.asList(type)));
		this.type = type;
		this.map = new HashMap<>();
	}

	@Override public RödaValue copy() {
		Map<String, RödaValue> newMap = new HashMap<>(map.size());
		for (Map.Entry<String, RödaValue> item : map.entrySet())
			newMap.put(item.getKey(), item.getValue().copy());
		return new RödaMap(newMap);
	}

	@Override public String str() {
		return "@(" + map.entrySet().stream()
			.map(e -> e.getKey() + " => " + e.getValue().str()).collect(joining("\n  ")) + ")";
	}

	@Override public Map<String, RödaValue> map() {
		return Collections.unmodifiableMap(map);
	}

	@Override public RödaValue get(RödaValue indexVal) {
		String index = indexVal.str();
		return map.get(index);
	}

	@Override public void set(RödaValue indexVal, RödaValue value) {
		String index = indexVal.str();
		if (type != null && !value.is(type))
			error("cannot put a " + value.typeString() + " to a " + typeString());
		map.put(index, value);
	}

	@Override public RödaValue contains(RödaValue indexVal) {
		String index = indexVal.str();
		return RödaBoolean.of(map.containsKey(index));
	}

	@Override public RödaValue length() {
		return RödaNumber.of(map.size());
	}

	@Override public boolean isMap() {
		return true;
	}

	@Override public boolean strongEq(RödaValue value) {
		if (!value.isMap()) return false;
		if (map.size() != value.map().size()) return false;
		boolean ans = true;
		for (int i = 0; i < map.size(); i++)
			ans &= map.get(i).strongEq(value.map().get(i));
		return ans;
	}

	public static RödaMap of(Map<String, RödaValue> map) {
		return new RödaMap(new HashMap<>(map));
	}

	public static RödaMap empty() {
		return new RödaMap(new HashMap<>());
	}

	public static RödaMap empty(Datatype type) {
		return new RödaMap(type);
	}
}
