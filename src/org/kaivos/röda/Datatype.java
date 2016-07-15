package org.kaivos.röda;

import static java.util.stream.Collectors.joining;

import java.util.Collections;
import java.util.List;

public class Datatype {
	public final String name;
	public final List<Datatype> subtypes;

	public Datatype(String name,
			List<Datatype> subtypes) {
		this.name = name;
		this.subtypes = Collections.unmodifiableList(subtypes);
	}

	public Datatype(String name) {
		this.name = name;
		this.subtypes = Collections.emptyList();
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
}