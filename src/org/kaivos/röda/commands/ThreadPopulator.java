package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkArgs;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.RödaValue.FUNCTION;

import java.util.Arrays;
import java.util.Collections;

import org.kaivos.röda.Builtins;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaException;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaStream;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.runtime.Datatype;
import org.kaivos.röda.runtime.Record;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaRecordInstance;

public final class ThreadPopulator {

	private ThreadPopulator() {}

	public static void populateThread(Interpreter I, RödaScope S) {
		Record threadRecord = new Record("Thread", Collections.emptyList(), Collections.emptyList(),
				Arrays.asList(new Record.Field("start", new Datatype("function")),
						new Record.Field("pull", new Datatype("function")),
						new Record.Field("tryPull", new Datatype("function")),
						new Record.Field("pullAll", new Datatype("function")),
						new Record.Field("peek", new Datatype("function")),
						new Record.Field("tryPeek", new Datatype("function")),
						new Record.Field("push", new Datatype("function"))),
				false, I.G);
		I.G.preRegisterRecord(threadRecord);
		I.G.postRegisterRecord(threadRecord);

		S.setLocal("thread", RödaNativeFunction.of("thread", (typeargs, args, kwargs, scope, in, out) -> {
			RödaValue function = args.get(0);

			RödaScope newScope = !function.is(RödaValue.NFUNCTION) && function.localScope() != null
					? new RödaScope(function.localScope()) : new RödaScope(I.G);
			RödaStream _in = RödaStream.makeStream();
			RödaStream _out = RödaStream.makeStream();

			class P {
				boolean started = false;
			}
			P p = new P();

			Runnable task = () -> {
				try {
					I.exec("<Thread.start>", 0, function,
							Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(),
							newScope, _in, _out);
				} catch (RödaException e) {
					System.err.println("[E] " + e.getMessage());
					for (String step : e.getStack()) {
						System.err.println(step);
					}
					if (e.getCause() != null)
						e.getCause().printStackTrace();
				}
				_out.finish();
			};

			RödaValue threadObject = RödaRecordInstance.of(threadRecord, Collections.emptyList());
			threadObject.setField("start", RödaNativeFunction.of("Thread.start", (ra, a, k, s, i, o) -> {
				checkArgs("Thread.start", 0, a.size());
				if (p.started)
					error("Thread has already been started");
				p.started = true;
				Interpreter.executor.execute(task);
			}, Collections.emptyList(), false));
			threadObject.setField("pull", Builtins.genericPull("Thread.pull", _out, false, true));
			threadObject.setField("tryPull", Builtins.genericTryPull("Thread.tryPull", _out, false));
			threadObject.setField("pullAll", Builtins.genericPull("Thread.pullAll", _out, false, false));
			
			threadObject.setField("peek", Builtins.genericPull("Thread.peek", _out, true, true));
			threadObject.setField("tryPeek", Builtins.genericTryPull("Thread.tryPeek", _out, true));
			
			threadObject.setField("push", Builtins.genericPush("Thread.push", _in, false));
			out.push(threadObject);
		}, Arrays.asList(new Parameter("runnable", false, FUNCTION)), false));
	}
}
