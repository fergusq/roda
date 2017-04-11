package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkList;
import static org.kaivos.röda.Interpreter.emptyStream;
import static org.kaivos.röda.Interpreter.typeMismatch;
import static org.kaivos.röda.RödaValue.FLOATING;
import static org.kaivos.röda.RödaValue.INTEGER;
import static org.kaivos.röda.RödaValue.LIST;
import static org.kaivos.röda.RödaValue.STRING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.ExpressionTree.CType;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaFloating;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaList;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public class ReducePopulator {

	private ReducePopulator() {}
	
	public static void addMinMaxFunction(RödaScope S, String name, boolean min) {
		S.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			RödaValue first = kwargs.get("fst");
			if (args.size() == 0) {
				RödaValue val = first != null ? first : in.pull();
				if (val == null) {
					emptyStream("empty stream");
					return;
				}
				while (true) {
					RödaValue val2 = in.pull();
					if (val2 == null) break;
					RödaValue a = min ? val2 : val;
					RödaValue b = min ? val : val2;
					if (a.callOperator(CType.LT, b).bool()) val = val2;
				}
				out.push(val);
			}
			else {
				for (RödaValue list : args) {
					checkList(name, list);
					if (list.list().isEmpty()) {
						if (first != null)
							out.push(first);
						else
							emptyStream("empty stream");
						continue;
					}
					RödaValue val = first != null ? first : list.list().get(0);
					for (int i = first != null ? 0 : 1; i < list.list().size(); i++) {
						RödaValue a = min ? list.list().get(i) : val;
						RödaValue b = min ? val : list.list().get(i);
						if (a.callOperator(CType.LT, b).bool()) val = list.list().get(i);
					}
					out.push(val);
				}
			}
		}, Arrays.asList(new Parameter("values", false, LIST)), true, Collections.emptyList(), true));
	}
	
	public static void populateReduce(RödaScope S) {
		S.setLocal("reduce", RödaNativeFunction.of("reduce", (typeargs, args, kwargs, scope, in, out) -> {
			if (in.open()) {
				in.unpull(args.get(0));
			}
			else {
				out.push(args.get(0));
			}
		}, Arrays.asList(new Parameter("value", false)), false));
		S.setLocal("reduceSteps", RödaNativeFunction.of("reduceSteps", (typeargs, args, kwargs, scope, in, out) -> {
			if (in.open()) {
				in.unpull(args.get(0));
			}
			out.push(args.get(0));
		}, Arrays.asList(new Parameter("value", false)), false));
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
				else typeMismatch("sum: expected list, integer of floating, got " + val.typeString());
			}
			else {
				for (RödaValue list : args) {
					checkList("sum", list);
					if (list.list().isEmpty()) {
						out.push(first != null ? first : RödaInteger.of(0));
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
					else typeMismatch("sum: expected list, integer or floating, got " + val.typeString());
				}
			}
		}, Arrays.asList(new Parameter("values", false, LIST)), true, Collections.emptyList(), true));
		S.setLocal("product", RödaNativeFunction.of("product", (typeargs, args, kwargs, scope, in, out) -> {
			RödaValue first = kwargs.get("fst");
			if (args.size() == 0) {
				RödaValue val = first != null ? first : in.pull();
				if (val == null) {
					out.push(RödaInteger.of(1));
					return;
				}
				if (val.is(INTEGER)) {
					long sum = val.integer();
					while (true) {
						RödaValue val2 = in.pull();
						if (val2 == null) break;
						sum *= val2.integer();
					}
					out.push(RödaInteger.of(sum));
				}
				else if (val.is(FLOATING)) {
					double sum = val.floating();
					while (true) {
						RödaValue val2 = in.pull();
						if (val2 == null) break;
						sum *= val2.floating();
					}
					out.push(RödaFloating.of(sum));
				}
				else typeMismatch("product: expected integer of floating, got " + val.typeString());
			}
			else {
				for (RödaValue list : args) {
					checkList("sum", list);
					if (list.list().isEmpty()) {
						out.push(first != null ? first : RödaInteger.of(1));
						continue;
					}
					RödaValue val = first != null ? first : list.list().get(0);
					if (val.is(INTEGER)) {
						long sum = val.integer();
						for (int i = first != null ? 0 : 1; i < list.list().size(); i++)
							sum *= list.list().get(i).integer();
						out.push(RödaInteger.of(sum));
					}
					else if (val.is(FLOATING)) {
						double sum = val.floating();
						for (int i = first != null ? 0 : 1; i < list.list().size(); i++)
							sum *= list.list().get(i).floating();
						out.push(RödaFloating.of(sum));
					}
					else typeMismatch("product: expected integer or floating, got " + val.typeString());
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
				else typeMismatch("concat: expected list or string, got " + val.typeString());
			}
			else {
				for (RödaValue list : args) {
					checkList("concat", list);
					if (list.list().isEmpty()) {
						out.push(first == null ? RödaList.empty() : RödaList.of(first));
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
					else typeMismatch("concat: expected list or string, got " + val.typeString());
				}
			}
		}, Arrays.asList(new Parameter("values", false, LIST)), true, Collections.emptyList(), true));
		addMinMaxFunction(S, "min", true);
		addMinMaxFunction(S, "max", false);
	}
	
}