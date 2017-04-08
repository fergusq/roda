package org.kaivos.röda.commands;

import static java.util.stream.Collectors.toList;
import static org.kaivos.röda.Interpreter.argumentOverflow;
import static org.kaivos.röda.Interpreter.checkString;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.JSON;
import org.kaivos.röda.JSON.JSONDouble;
import org.kaivos.röda.JSON.JSONElement;
import org.kaivos.röda.JSON.JSONInteger;
import org.kaivos.röda.JSON.JSONList;
import org.kaivos.röda.JSON.JSONMap;
import org.kaivos.röda.JSON.JSONString;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.RödaStream;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaFloating;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaList;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class JsonPopulator {

	private JsonPopulator() {}
	
	private static void handle(RödaStream out, String code) {
		JSONElement root = JSON.parseJSON(code);
		// rekursiivinen ulostulo
		// apuluokka rekursion mahdollistamiseksi
		class R<I> { I i; }
		R<java.util.function.Function<JSONElement, RödaValue>>
		makeRöda = new R<>();
		makeRöda.i = json -> {
			RödaValue elementName = RödaString.of(json.getElementName());
			RödaValue value;
			if (json instanceof JSONInteger) {
				value = RödaInteger.of(((JSONInteger) json).getValue());
			}
			else if (json instanceof JSONDouble) {
				value = RödaFloating.of(((JSONDouble) json).getValue());
			}
			else if (json instanceof JSONString) {
				value = RödaString.of(((JSONString) json).getValue());
			}
			else if (json instanceof JSONList) {
				value = RödaList.of(((JSONList) json).getElements()
						      .stream()
						      .map(j -> makeRöda.i.apply(j))
						      .collect(toList()));
			}
			else if (json instanceof JSONMap) {
				value = RödaList.of(((JSONMap) json).getElements().entrySet()
						      .stream()
						      .map(e -> RödaList.of(RödaString.of(e.getKey().getKey()),
									      makeRöda.i.apply(e.getValue())))
						      .collect(toList()));
			}
			else {
				value = RödaString.of(json.toString());
			}
			return RödaList.of(elementName, value);
		};
		out.push(makeRöda.i.apply(root));
	}

	public static void populateJson(RödaScope S) {
		S.setLocal("json", RödaNativeFunction.of("json", (typeargs, args, kwargs, scope, in, out) -> {
					if (args.size() > 1) argumentOverflow("json", 1, args.size());
					else if (args.size() == 1) {
						RödaValue arg = args.get(0);
						checkString("json", arg);
						String code = arg.str();
						handle(out, code);
					}
					else {
						RödaValue value;
						while ((value = in.pull()) != null) {
							checkString("json", value);
							String code = value.str();
							handle(out, code);
						}
					}
				}, Arrays.asList(new Parameter("flags_and_code", false)), true));
	}
}
