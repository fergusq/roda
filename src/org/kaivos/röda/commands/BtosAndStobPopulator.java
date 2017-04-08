package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkList;
import static org.kaivos.röda.Interpreter.checkInteger;
import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.Interpreter.outOfBounds;
import static org.kaivos.röda.RödaValue.LIST;
import static org.kaivos.röda.RödaValue.STRING;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaList;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class BtosAndStobPopulator {

	private BtosAndStobPopulator() {}

	public static void populateBtosAndStob(RödaScope S) {
		S.setLocal("bytesToString", RödaNativeFunction.of("bytesToString", (typeargs, args, kwargs, scope, in, out) -> {
			Charset chrset = StandardCharsets.UTF_8;
			Consumer<RödaValue> convert = v -> {
				checkList("bytesToString", v);
				byte[] arr = new byte[(int) v.list().size()];
				int c = 0;
				for (RödaValue i : v.list()) {
					checkInteger("bytesToString", i);
					long l = i.integer();
					if (l > Byte.MAX_VALUE * 2)
						outOfBounds("byteToString: too large byte: " + l);
					arr[c++] = (byte) l;
				}
				out.push(RödaString.of(new String(arr, chrset)));
			};
			if (args.size() > 0) {
				args.forEach(convert);
			} else {
				in.forAll(convert);
			}
		}, Arrays.asList(new Parameter("lists", false, LIST)), true));

		S.setLocal("stringToBytes", RödaNativeFunction.of("stringToBytes", (typeargs, args, kwargs, scope, in, out) -> {
			Charset chrset = StandardCharsets.UTF_8;
			Consumer<RödaValue> convert = v -> {
				checkString("stringToBytes", v);
				byte[] arr = v.str().getBytes(chrset);
				List<RödaValue> bytes = new ArrayList<>();
				for (byte b : arr)
					bytes.add(RödaInteger.of(b));
				out.push(RödaList.of(bytes));
			};
			if (args.size() > 0) {
				args.forEach(convert);
			} else {
				in.forAll(convert);
			}
		}, Arrays.asList(new Parameter("strings", false, STRING)), true));
	}
}
