package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentOverflow;
import static org.kaivos.röda.Interpreter.unknownName;
import static org.kaivos.röda.RödaValue.NAMESPACE;
import static org.kaivos.röda.RödaValue.STRING;

import java.util.Arrays;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;

public final class AssignGlobalPopulator {
	
	private AssignGlobalPopulator() {}

	public static void populateAssignGlobal(RödaScope S) {
		S.setLocal("assignGlobal", RödaNativeFunction.of("assignGlobal", (typeargs, args, kwargs, scope, in, out) -> {
			String variableName = args.get(0).str();
			S.setLocal(variableName, args.get(1));
	    }, Arrays.asList(new Parameter("variable", false, STRING), new Parameter("value", false)), false));
	
	    S.setLocal("createGlobal", RödaNativeFunction.of("createGlobal", (typeargs, args, kwargs, scope, in, out) -> {
			String variableName = args.get(0).str();
			if (S.resolve(variableName) == null)
				S.setLocal(variableName, args.get(1));
	    }, Arrays.asList(new Parameter("variable", false, STRING), new Parameter("value", false)), false));
		
	    S.setLocal("assignGlobalType", RödaNativeFunction.of("assignGlobalType", (typeargs, args, kwargs, scope, in, out) -> {
			String typename = args.get(0).str();
			if (args.size() > 2) argumentOverflow("assignGlobalType", 2, args.size());
			RödaScope typeScope = args.size() == 1 ? scope : args.get(1).scope();
			if (!typeScope.getRecords().containsKey(typename))
				unknownName("record class '" + typename + "' not found");
			S.registerRecord(typeScope.getRecordDeclarations().get(typename));
	    }, Arrays.asList(
	    		new Parameter("typename", false, STRING),
	    		new Parameter("namespace", false, NAMESPACE)), true));
	
	    S.setLocal("createGlobalType", RödaNativeFunction.of("createGlobalType", (typeargs, args, kwargs, scope, in, out) -> {
			String typename = args.get(0).str();
			if (args.size() > 2) argumentOverflow("createGlobalType", 2, args.size());
			RödaScope typeScope = args.size() == 1 ? scope : args.get(1).scope();
			if (!typeScope.getRecords().containsKey(typename))
				unknownName("record class '" + typename + "' not found");
			if (!S.getRecords().containsKey(typename))
				S.registerRecord(typeScope.getRecordDeclarations().get(typename));
	    }, Arrays.asList(
	    		new Parameter("typename", false, STRING),
	    		new Parameter("namespace", false, NAMESPACE)), true));
	}
}
