package org.kaivos.röda.runtime;

import static org.kaivos.röda.Interpreter.unknownName;
import static java.util.stream.Collectors.joining;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.kaivos.röda.RödaValue;
import org.kaivos.röda.Interpreter.RecordDeclaration;
import org.kaivos.röda.Interpreter.RödaScope;

public class Datatype {
	public final String name;
	public final List<Datatype> subtypes;
	public final Optional<RödaScope> scope;

	public Datatype(String name,
			List<Datatype> subtypes,
			Optional<RödaScope> scope) {
		this.name = name;
		this.subtypes = Collections.unmodifiableList(subtypes);
		this.scope = scope;
	}
	
	public Datatype(String name, List<Datatype> subtypes, RödaScope scope) {
		this(name, subtypes, Optional.of(scope));
	}
	
	public Datatype(String name, List<Datatype> subtypes) {
		this(name, subtypes, Optional.empty());
	}

	public Datatype(String name, RödaScope scope) {
		this.name = name;
		this.subtypes = Collections.emptyList();
		this.scope = Optional.of(scope);
	}

	public Datatype(String name) {
		this.name = name;
		this.subtypes = Collections.emptyList();
		this.scope = Optional.empty();
	}
	
	private RecordDeclaration resolveDeclaration() {
		if (scope.isPresent()) {
			RecordDeclaration r = scope.get().getRecordDeclarations().get(name);
			if (r == null)
				unknownName("record class '" + name + "' not found");
			return r;
		}
		unknownName("record class '" + name + "' not found (namespace not specified)");
		return null;
	}
	
	public Record resolve() {
		return resolveDeclaration().tree;
	}
	
	public RödaValue resolveReflection() {
		return resolveDeclaration().reflection;
	}

	@Override
	public String toString() {
		if (subtypes.isEmpty())
			return name;
		return name + "<" + subtypes.stream()
			.map(Datatype::toString)
			.collect(joining(", ")) + ">";
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Datatype))
			return false;
		Datatype other = (Datatype) obj;
		if (!name.equals(other.name))
			return false;
		if (!subtypes.equals(other.subtypes))
			return false;

		return true;
	}
	
	@Override
	public int hashCode() {
		return name.hashCode() + subtypes.hashCode();
	}
}