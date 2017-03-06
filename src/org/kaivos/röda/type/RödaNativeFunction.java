package org.kaivos.röda.type;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.kaivos.röda.RödaStream;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Datatype;
import org.kaivos.röda.runtime.Function.Parameter;

import static org.kaivos.röda.Interpreter.RödaScope;

public class RödaNativeFunction extends RödaValue {
	public static class NativeFunction {
		public String name;
		public NativeFunctionBody body;
		public boolean isVarargs, isKwVarargs;
		public List<Parameter> parameters, kwparameters;
	}
	
	public static interface NativeFunctionBody {
		public void exec(List<Datatype> typeargs,
				List<RödaValue> args,
				Map<String, RödaValue> kwargs,
				RödaScope scope,
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
	
	@Override
	public int hashCode() {
		return function.body.hashCode();
	}

	public static RödaNativeFunction of(NativeFunction function) {
		return new RödaNativeFunction(function);
	}
	
	public static RödaNativeFunction of(String name, NativeFunctionBody body,
			List<Parameter> parameters, boolean isVarargs) {
		return of(name, body, parameters, isVarargs, Collections.emptyList());
	}

	public static RödaNativeFunction of(String name, NativeFunctionBody body,
			List<Parameter> parameters, boolean isVarargs, List<Parameter> kwparameters) {
		return of(name, body, parameters, isVarargs, kwparameters, false);
	}

	public static RödaNativeFunction of(String name, NativeFunctionBody body,
			List<Parameter> parameters, boolean isVarargs, List<Parameter> kwparameters, boolean isKwVarargs) {
		
		for (Parameter p : parameters)
			if (p.defaultValue != null)
				throw new IllegalArgumentException("non-kw parameters can't have default values");
		
		for (Parameter p : kwparameters)
			if (p.defaultValue == null)
				throw new IllegalArgumentException("kw parameters must have default values");
		
		NativeFunction function = new NativeFunction();
		function.name = name;
		function.body = body;
		function.isVarargs = isVarargs;
		function.isKwVarargs = isKwVarargs;
		function.parameters = parameters;
		function.kwparameters = kwparameters;
		return of(function);
	}
}
