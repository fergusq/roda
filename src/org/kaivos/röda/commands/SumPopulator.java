package org.kaivos.röda.commands;

import static org.kaivos.röda.RödaValue.FLOATING;
import static org.kaivos.röda.RödaValue.INTEGER;
import static org.kaivos.röda.RödaValue.LIST;
import static org.kaivos.röda.RödaValue.STRING;
import static org.kaivos.röda.Interpreter.typeMismatch;
import static org.kaivos.röda.Interpreter.checkList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaFloating;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaList;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public class SumPopulator {

	private SumPopulator() {}
	
	public static void populateSum(RödaScope S) {
		S.setLocal("sum", RödaNativeFunction.of("sum", (typeargs, args, kwargs, scope, in, out) -> {
			RödaValue first = kwargs.get("fst");
			if (args.size() == 0) {
				RödaValue val = first != null ? first : in.pull();
				if (val == null) {
					out.push(RödaInteger.of(0));
					return;
				}
				if (val.is(INTEGER)) {
					long sum = val.integer();
					while (true) {
						RödaValue val2 = in.pull();
						if (val2 == null) break;
						sum += val2.integer();
					}
					out.push(RödaInteger.of(sum));
				}
				else if (val.is(FLOATING)) {
					double sum = val.floating();
					while (true) {
						RödaValue val2 = in.pull();
						if (val2 == null) break;
						sum += val2.floating();
					}
					out.push(RödaFloating.of(sum));
				}
				else if (val.is(LIST)) {
					List<RödaValue> sum = new ArrayList<>(val.list());
					while (true) {
						RödaValue val2 = in.pull();
						if (val2 == null) break;
						sum.add(val2);
					}
					out.push(RödaList.of(sum));
				}
				else typeMismatch("sum expected list, integer of floating, got " + val.typeString());
			}
			else {
				for (RödaValue list : args) {
					checkList("sum", list);
					if (list.list().isEmpty()) {
						out.push(RödaInteger.of(0));
						continue;
					}
					RödaValue val = first != null ? first : list.list().get(0);
					if (val.is(INTEGER)) {
						long sum = val.integer();
						for (int i = first != null ? 0 : 1; i < list.list().size(); i++)
							sum += list.list().get(i).integer();
						out.push(RödaInteger.of(sum));
					}
					else if (val.is(FLOATING)) {
						double sum = val.floating();
						for (int i = first != null ? 0 : 1; i < list.list().size(); i++)
							sum += list.list().get(i).floating();
						out.push(RödaFloating.of(sum));
					}
					else if (val.is(LIST)) {
						List<RödaValue> sum = new ArrayList<>(val.list());
						for (int i = first != null ? 0 : 1; i < list.list().size(); i++)
							sum.add(list.list().get(i));
						out.push(RödaList.of(sum));
					}
					else typeMismatch("sum expected list, integer or floating, got " + val.typeString());
				}
			}
		}, Arrays.asList(new Parameter("values", false, LIST)), true, Collections.emptyList(), true));
		S.setLocal("concat", RödaNativeFunction.of("concat", (typeargs, args, kwargs, scope, in, out) -> {
			RödaValue first = kwargs.get("fst");
			if (args.size() == 0) {
				RödaValue val = first != null ? first : in.pull();
				if (val == null) {
					out.push(RödaList.empty());
					return;
				}
				if (val.is(LIST)) {
					List<RödaValue> sum = new ArrayList<>(val.list());
					while (true) {
						RödaValue val2 = in.pull();
						if (val2 == null) break;
						sum.addAll(val2.list());
					}
					out.push(RödaList.of(sum));
				}
				else if (val.is(STRING)) {
					StringBuilder sum = new StringBuilder(val.str());
					while (true) {
						RödaValue val2 = in.pull();
						if (val2 == null) break;
						sum.append(val2.str());
					}
					out.push(RödaString.of(sum.toString()));
				}
				else typeMismatch("concat expected list or string, got " + val.typeString());
			}
			else {
				for (RödaValue list : args) {
					checkList("concat", list);
					if (list.list().isEmpty()) {
						out.push(RödaList.empty());
						continue;
					}
					RödaValue val = first != null ? first : list.list().get(0);
					if (val.is(LIST)) {
						List<RödaValue> sum = new ArrayList<>(val.list());
						for (int i = first != null ? 0 : 1; i < list.list().size(); i++)
							sum.addAll(list.list().get(i).list());
						out.push(RödaList.of(sum));
					}
					else if (val.is(STRING)) {
						StringBuilder sum = new StringBuilder(val.str());
						for (int i = first != null ? 0 : 1; i < list.list().size(); i++)
							sum.append(list.list().get(i).str());
						out.push(RödaString.of(sum.toString()));
					}
					else typeMismatch("concat expected list or string, got " + val.typeString());
				}
			}
		}, Arrays.asList(new Parameter("values", false, LIST)), true, Collections.emptyList(), true));
	}
	
}