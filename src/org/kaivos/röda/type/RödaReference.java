package org.kaivos.röda.type;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Interpreter.RödaScope;

public class RödaReference extends RödaValue {
	private String target;
	private RödaScope scope;

	private RödaReference(String target, RödaScope scope) {
		assumeIdentity("reference");
		this.target = target;
		this.scope = scope;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public String str() {
		return "&" + target;
	}

	@Override public String target() {
		return target;
	}

	@Override public RödaValue resolve(boolean implicite) {
		RödaValue t = scope.resolve(target);
		if (t == null) error("variable not found (via " + (implicite ? "implicite" : "explicite")
				     + " reference): " + target);
		return t;
	}

	@Override public RödaValue unsafeResolve() {
		return scope.resolve(target);
	}

	@Override public RödaValue impliciteResolve() {
		return resolve(true);
	}

	@Override public void assign(RödaValue value) {
		scope.set(target, value);
	}

	@Override public void assignLocal(RödaValue value) {
		scope.setLocal(target, value);
	}

	@Override public boolean isReference() {
		return true;
	}

	@Override public boolean strongEq(RödaValue value) {
		error("can't compare a reference");
		return false;
	}

	public static RödaReference of(String target, RödaScope scope) {
		return new RödaReference(target, scope);
	}
}
