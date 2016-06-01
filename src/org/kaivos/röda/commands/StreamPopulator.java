package org.kaivos.röda.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Supplier;

import org.kaivos.röda.Builtins;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Datatype;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.Parser.Record;
import org.kaivos.röda.RödaStream;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaRecordInstance;

public final class StreamPopulator {

	private StreamPopulator() {}

	public static void populateStream(Interpreter I, RödaScope S) {
		Record streamRecord = new Record("Stream",
						 Collections.emptyList(),
						 null,
						 Arrays.asList(new Record.Field("pull", new Datatype("function")),
							       new Record.Field("push", new Datatype("function")),
							       new Record.Field("finish", new Datatype("function"))),
						 false);
		I.registerRecord(streamRecord);
	
		Supplier<RödaValue> getStreamObj = () -> {
			RödaStream stream = RödaStream.makeStream();
			RödaValue streamObject = RödaRecordInstance.of(streamRecord,
								       Collections.emptyList(),
								       I.records);
			streamObject.setField("pull", Builtins.genericPull("Stream.pull", stream));
			streamObject.setField("push", Builtins.genericPush("Stream.push", stream));
			streamObject.setField("finish", RödaNativeFunction
					      .of("Stream.finish", (ta, a, s, i, o) -> {
							      stream.finish();
						      }, Collections.emptyList(), false));
			return streamObject;
		};
	
		S.setLocal("stream", RödaNativeFunction.of("stream", (typeargs, args, scope, in, out) -> {
					if (args.size() == 0) {
						out.push(getStreamObj.get());
						return;
					}
					for (RödaValue ref : args) {
						ref.assignLocal(getStreamObj.get());
					}
				}, Arrays.asList(new Parameter("variables", true)), true));
	}
}
