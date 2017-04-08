package org.kaivos.röda.runtime;

import java.util.List;

import org.kaivos.röda.Parser.StatementTree;
import org.kaivos.röda.Parser.ExpressionTree;

public class Function {
	public String name;
	public List<String> typeparams;
	public List<Parameter> parameters, kwparameters;
	public boolean isVarargs;
	public List<StatementTree> body;

	public Function(String name,
		 List<String> typeparams,
		 List<Parameter> parameters,
		 boolean isVarargs,
		 List<Parameter> kwparameters,
		 List<StatementTree> body) {
		
		for (Parameter p : parameters)
			if (p.defaultValue != null)
				throw new IllegalArgumentException("non-kw parameters can't have default values");
		
		for (Parameter p : kwparameters)
			if (p.defaultValue == null)
				throw new IllegalArgumentException("kw parameters must have default values");

		this.name = name;
		this.typeparams = typeparams;
		this.parameters = parameters;
		this.kwparameters = kwparameters;
		this.isVarargs = isVarargs;
		this.body = body;
	}

	public static class Parameter {
		public String name;
		public boolean reference;
		public Datatype type;
		public ExpressionTree defaultValue;
	    public Parameter(String name, boolean reference) {
			this(name, reference, null, null);
		}
	    public Parameter(String name, boolean reference, Datatype type) {
			this(name, reference, type, null);
		}
	    public Parameter(String name, boolean reference, ExpressionTree dafaultValue) {
			this(name, reference, null, dafaultValue);
		}
	    public Parameter(String name, boolean reference, Datatype type, ExpressionTree defaultValue) {
	    	if (reference && defaultValue != null)
	    		throw new IllegalArgumentException("a reference parameter can't have a default value");
			this.name = name;
			this.reference = reference;
			this.type = type;
			this.defaultValue = defaultValue;
		}
	}
}