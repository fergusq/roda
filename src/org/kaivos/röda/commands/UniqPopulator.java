package org.kaivos.röda.commands;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;

public class UniqPopulator {

	private UniqPopulator() {}
	
	private static class MutableInt { int i = 1; };
	private static class MutableValue { RödaValue v = null; };
	
	private static void addUniqFunction(RödaScope S, String name, boolean count) {
		S.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			MutableValue previous = new MutableValue();
			MutableInt i = new MutableInt();
			in.forAll(value -> {
				if (previous.v == null || !value.strongEq(previous.v)){
					if (count && previous.v != null) out.push(RödaInteger.of(i.i));
					out.push(value);
					i.i = 1;
				}
				else {
					i.i++;
				}
				previous.v = value;
			});
			if (count) out.push(RödaInteger.of(i.i));
		}, Collections.emptyList(), false));
	}
	
	private static void addUnorderedUniqFunction(RödaScope S, String name, boolean count) {
		S.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			Map<RödaValue, Integer> counts = new HashMap<>();
			in.forAll(value -> {
				if (counts.containsKey(value)) {
					counts.put(value, Integer.valueOf(counts.get(value).intValue() + 1));
				}
				else {
					counts.put(value, Integer.valueOf(1));
				}
			});
			if (count) {
				for (Entry<RödaValue, Integer> entry : counts.entrySet()) {
					out.push(entry.getKey());
					out.push(RödaInteger.of(entry.getValue()));
				}
			}
			else {
				counts.keySet().forEach(out::push);
			}
		}, Collections.emptyList(), false));
	}
	
	private static void addOrderedUniqFunction(RödaScope S, String name) {
		S.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			Set<RödaValue> counts = new HashSet<>();
			in.forAll(value -> {
				if (!counts.contains(value)) {
					counts.add(value);
					out.push(value);
				}
			});
		}, Collections.emptyList(), false));
	}
	
	public static void populateUniq(RödaScope S) {
		addUniqFunction(S, "uniq", false);
		addUniqFunction(S, "count", true);
		addUnorderedUniqFunction(S, "unorderedUniq", false);
		addUnorderedUniqFunction(S, "unorderedCount", true);
		addOrderedUniqFunction(S, "orderedUniq");
	}
	
}
