package org.kaivos.röda.type;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Interpreter.unknownName;
import static org.kaivos.röda.Interpreter.RödaScope;

public class RödaReference extends RödaValue {
	private String target;
	private RödaScope scope;
	
	private String file;
	private int line;

	private RödaReference(String target, RödaScope scope, String file, int line) {
		assumeIdentity(REFERENCE);
		this.target = target;
		this.scope = scope;
		this.file = file;
		this.line = line;
	}

	@Override public RödaValue copy() {
		return this;
	}

	@Override public String str() {
		RödaValue targetVal = unsafeResolve();
		return "<reference &" + target + " to " + (targetVal == null ? "<nothing>" : targetVal.str()) + ">";
	}

	@Override public String target() {
		return target;
	}

	@Override public RödaValue resolve(boolean implicite) {
		RödaValue t = scope.resolve(target);
		if (t == null) unknownName("variable not found " + (implicite ? "" : "(via explicite reference)")
				     + ": " + target + " (at " + file + ":" + line + ")");
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

	@Override public boolean strongEq(RödaValue value) {
		error("can't compare a reference");
		return false;
	}
	
	@Override
	public int hashCode() {
		return target.hashCode() + scope.hashCode();
	}

	public static RödaReference of(String target, RödaScope scope, String file, int line) {
		return new RödaReference(target, scope, file, line);
	}
}
