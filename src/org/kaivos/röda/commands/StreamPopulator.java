package org.kaivos.röda.commands;

import java.util.Arrays;
import java.util.Collections;

import org.kaivos.röda.Builtins;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaStream;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Datatype;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.runtime.Record;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaRecordInstance;

public final class StreamPopulator {

	private StreamPopulator() {
	}
	
	private static Record streamRecord = null;
	
	public static RödaValue createStreamObj(RödaStream stream) {
		if (streamRecord == null) throw new RuntimeException("Stream record isn't created yet");
		
		RödaValue streamObject = RödaRecordInstance.of(streamRecord, Collections.emptyList());
		
		streamObject.setField("pull", Builtins.genericPull("Stream.pull", stream, false, true));
		streamObject.setField("tryPull", Builtins.genericTryPull("Stream.tryPull", stream, false));
		streamObject.setField("pullAll", Builtins.genericPull("Stream.pullAll", stream, false, false));
		
		streamObject.setField("peek", Builtins.genericPull("Stream.peek", stream, true, true));
		streamObject.setField("tryPeek", Builtins.genericTryPull("Stream.tryPeek", stream, true));
		
		streamObject.setField("push", Builtins.genericPush("Stream.push", stream, false));
		streamObject.setField("unpull", Builtins.genericPush("Stream.unpull", stream, true));
		streamObject.setField("finish", RödaNativeFunction.of("Stream.finish", (ta, a, k, s, i, o) -> {
			stream.finish();
		}, Collections.emptyList(), false));
		return streamObject;
	}

	public static void populateStream(Interpreter I, RödaScope S) {
		streamRecord = new Record("Stream", Collections.emptyList(), Collections.emptyList(),
				Arrays.asList(
						new Record.Field("pull", new Datatype("function")),
						new Record.Field("tryPull", new Datatype("function")),
						new Record.Field("pullAll", new Datatype("function")),
						new Record.Field("peek", new Datatype("function")),
						new Record.Field("tryPeek", new Datatype("function")),
						new Record.Field("push", new Datatype("function")),
						new Record.Field("unpull", new Datatype("function")),
						new Record.Field("finish", new Datatype("function"))),
				false, I.G);
		I.G.preRegisterRecord(streamRecord);
		I.G.postRegisterRecord(streamRecord);

		S.setLocal("stream", RödaNativeFunction.of("stream", (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() == 0) {
				out.push(createStreamObj(RödaStream.makeStream()));
				return;
			}
			for (RödaValue ref : args) {
				ref.assignLocal(createStreamObj(RödaStream.makeStream()));
			}
		}, Arrays.asList(new Parameter("variables", true)), true));
	}
}
