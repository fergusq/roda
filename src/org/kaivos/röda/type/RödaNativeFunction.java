package org.kaivos.röda.type;

import java.util.List;

import org.kaivos.röda.Datatype;
import org.kaivos.röda.RödaStream;
import org.kaivos.röda.RödaValue;

import static org.kaivos.röda.Interpreter.RödaScope;
import static org.kaivos.röda.Parser.Parameter;

public class RödaNativeFunction extends RödaValue {
	public static class NativeFunction {
		public String name;
		public NativeFunctionBody body;
		public boolean isVarargs;
		public List<Parameter> parameters;
	}
	
	public static interface NativeFunctionBody {
		public void exec(List<Datatype> typeargs, List<RödaValue> args, RödaScope scope,
				 RödaStream in, RödaStream out);
	}

	private NativeFunction function;

	private RödaNativeFunction(NativeFunction function) {
		assumeIdentity(NFUNCTION);
		assumeIdentity(FUNCTION);
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

	@Override public boolean strongEq(RödaValue value) {
		return value.is(NFUNCTION) && value.nfunction() == function;
	}

	public static RödaNativeFunction of(NativeFunction function) {
		return new RödaNativeFunction(function);
	}

	public static RödaNativeFunction of(String name, NativeFunctionBody body,
					    List<Parameter> parameters, boolean isVarargs) {
		NativeFunction function = new NativeFunction();
		function.name = name;
		function.body = body;
		function.isVarargs = isVarargs;
		function.parameters = parameters;
		return of(function);
	}
}
