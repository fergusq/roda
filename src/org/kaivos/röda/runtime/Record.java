package org.kaivos.röda.runtime;

import java.util.Collections;
import java.util.List;

import org.kaivos.röda.Parser.AnnotationTree;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.ExpressionTree;

public class Record {
	public static class Field {
		public final String name;
		public final Datatype type;
		public final ExpressionTree defaultValue;
		public final List<AnnotationTree> annotations;

		public Field(String name,
		      Datatype type) {
			this(name, type, null, Collections.emptyList());
		}

		public Field(String name,
		      Datatype type,
		      ExpressionTree defaultValue,
		      List<AnnotationTree> annotations) {
			this.name = name;
			this.type = type;
			this.defaultValue = defaultValue;
			this.annotations = Collections.unmodifiableList(annotations);
		}
	}
	public static class SuperExpression {
		public final Datatype type;
		public final List<ExpressionTree> args;
		
		public SuperExpression(Datatype type, List<ExpressionTree> args) {
			this.type = type;
			this.args = args;
		}
	}

	public final String name;
	public final List<String> typeparams, params;
	public final List<SuperExpression> superTypes;
	public final List<AnnotationTree> annotations;
	public final List<Field> fields;
	public final boolean isValueType;
	public final RödaScope declarationScope;

	public Record(String name,
	       List<String> typeparams,
	       List<SuperExpression> superTypes,
	       List<Field> fields,
	       boolean isValueType,
	       RödaScope declarationScope) {
		this(name, typeparams, Collections.emptyList(), superTypes, Collections.emptyList(), fields, isValueType, declarationScope);
	}

	public Record(String name,
	       List<String> typeparams,
	       List<String> params,
	       List<SuperExpression> superTypes,
	       List<AnnotationTree> annotations,
	       List<Field> fields,
	       boolean isValueType,
	       RödaScope declarationScope) {
		this.name = name;
		this.typeparams = Collections.unmodifiableList(typeparams);
		this.params = Collections.unmodifiableList(params);
		this.annotations = Collections.unmodifiableList(annotations);
		this.fields = Collections.unmodifiableList(fields);
		this.superTypes = superTypes;
		this.isValueType = isValueType;
		this.declarationScope = declarationScope;
	}
}