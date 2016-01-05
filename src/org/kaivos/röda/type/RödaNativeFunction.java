package org.kaivos.röda.type;

import java.util.List;

import org.kaivos.röda.RödaStream;
import static org.kaivos.röda.RödaStream.StreamType;
import static org.kaivos.röda.RödaStream.ValueStream;
import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Interpreter.RödaScope;
import static org.kaivos.röda.Parser.Parameter;

public class RödaNativeFunction extends RödaValue {
	public static class NativeFunction {
		public String name;
		public NativeFunctionBody body;
		public boolean isVarargs;
		public List<Parameter> parameters;
		public StreamType input;
		public StreamType output;
	}
	
	public static interface NativeFunctionBody {
		public void exec(List<RödaValue> rawArgs, List<RödaValue> args, RödaScope scope,
				 RödaStream in, RödaStream out);
	}

	private NativeFunction function;

	private RödaNativeFunction(NativeFunction function) {
		assumeIdentity("function");
		assumeIdentity("nfunction");
		this.function = function;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public NativeFunction nfunction() {
		return function;
	}

	@Override public String str() {
		return "<nfunction '"+function.name+"'>";
	}

	@Override public String typeString() {
		return "nfunction";
	}

	@Override public boolean isFunction() {
		return true;
	}

	@Override public boolean isNativeFunction() {
		return true;
	}

	@Override public boolean strongEq(RödaValue value) {
		return value.isNativeFunction() && value.nfunction() == function;
	}

	public static RödaNativeFunction of(NativeFunction function) {
		return new RödaNativeFunction(function);
	}

	public static RödaNativeFunction of(String name, NativeFunctionBody body,
					    List<Parameter> parameters, boolean isVarargs) {
		return of(name, body, parameters, isVarargs,
			  new ValueStream(), new ValueStream());
	}

	public static RödaNativeFunction of(String name, NativeFunctionBody body,
					    List<Parameter> parameters, boolean isVarargs,
					    StreamType input, StreamType output) {
		NativeFunction function = new NativeFunction();
		function.name = name;
		function.body = body;
		function.isVarargs = isVarargs;
		function.parameters = parameters;
		function.input = input;
		function.output = output;
		return of(function);
	}
}
