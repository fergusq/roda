package org.kaivos.röda;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

import org.kaivos.röda.IOUtils;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.commands.AssignGlobalPopulator;
import org.kaivos.röda.commands.BtosAndStobPopulator;
import org.kaivos.röda.commands.CdAndPwdPopulator;
import org.kaivos.röda.commands.CurrentTimePopulator;
import org.kaivos.röda.commands.ErrorPopulator;
import org.kaivos.röda.commands.ErrprintPopulator;
import org.kaivos.röda.commands.ExecPopulator;
import org.kaivos.röda.commands.FilePopulator;
import org.kaivos.röda.commands.GetenvPopulator;
import org.kaivos.röda.commands.HeadAndTailPopulator;
import org.kaivos.röda.commands.IdentityPopulator;
import org.kaivos.röda.commands.ImportPopulator;
import org.kaivos.röda.commands.JsonPopulator;
import org.kaivos.röda.commands.MatchPopulator;
import org.kaivos.röda.commands.NamePopulator;
import org.kaivos.röda.commands.ParseNumPopulator;
import org.kaivos.röda.commands.PushAndPullPopulator;
import org.kaivos.röda.commands.RandomPopulator;
import org.kaivos.röda.commands.ReadAndWritePopulator;
import org.kaivos.röda.commands.ReplacePopulator;
import org.kaivos.röda.commands.SearchPopulator;
import org.kaivos.röda.commands.SeqPopulator;
import org.kaivos.röda.commands.ServerPopulator;
import org.kaivos.röda.commands.SplitPopulator;
import org.kaivos.röda.commands.StreamPopulator;
import org.kaivos.röda.commands.StrsizePopulator;
import org.kaivos.röda.commands.ThreadPopulator;
import org.kaivos.röda.commands.TrueAndFalsePopulator;
import org.kaivos.röda.commands.UndefinePopulator;
import org.kaivos.röda.commands.WcatPopulator;

import org.kaivos.röda.type.*;
import static org.kaivos.röda.Interpreter.*;
import static org.kaivos.röda.Parser.*;

public class Builtins {

	private Builtins() {}

	static void populate(Interpreter I) {
		RödaScope S = I.G;

		/* Perusvirtaoperaatiot */

		S.setLocal("print", RödaNativeFunction.of("print", (typeargs, args, kwargs, scope, in, out) -> {
					if (args.isEmpty()) {
						argumentUnderflow("print", 1, 0);
						return;
					}
					for (RödaValue value : args) {
						out.push(value);
					}
					out.push(RödaString.of("\n"));
				}, Arrays.asList(new Parameter("values", false)), true));
		
		PushAndPullPopulator.populatePushAndPull(S);

		/* Muuttujaoperaatiot */

		UndefinePopulator.populateUndefine(S);
		NamePopulator.populateName(S);
		ImportPopulator.populateImport(I, S);
		AssignGlobalPopulator.populateAssignGlobal(S);

		/* Muut oleelliset kielen rakenteet */

		IdentityPopulator.populateIdentity(S);
		ErrorPopulator.populateError(S);
		ErrprintPopulator.populateErrprint(S);

		/* Täydentävät virtaoperaatiot */

		HeadAndTailPopulator.populateHeadAndTail(S);

		/* Yksinkertaiset merkkijonopohjaiset virtaoperaatiot */

		SearchPopulator.populateSearch(S);
		MatchPopulator.populateMatch(S);
		ReplacePopulator.populateReplace(S);

		/* Parserit */

		SplitPopulator.populateSplit(S);
		JsonPopulator.populateJson(S);
		ParseNumPopulator.populateParseNum(S);
		BtosAndStobPopulator.populateBtosAndStob(S);
		StrsizePopulator.populateStrsize(S);

		/* Konstruktorit */

		SeqPopulator.populateSeq(S);
		TrueAndFalsePopulator.populateTrueAndFalse(S);
		StreamPopulator.populateStream(I, S);

		/* Apuoperaatiot */

		CurrentTimePopulator.populateTime(S);
		RandomPopulator.populateRandom(S);
		ExecPopulator.populateExec(I, S);
		GetenvPopulator.populateGetenv(S);

		/* Tiedosto-operaatiot */

		CdAndPwdPopulator.populateCdAndPwd(I, S);
		ReadAndWritePopulator.populateReadAndWrite(I, S);
		FilePopulator.populateFile(I, S);

		/* Verkko-operaatiot */

		WcatPopulator.populateWcat(S);
		ServerPopulator.populateServer(I, S);

		// Säikeet

		ThreadPopulator.populateThread(I, S);
	}

	public static RödaValue genericPush(String name, RödaStream _out) {
		return RödaNativeFunction
			.of(name,
			    (ra, a, k, s, i, o) -> {
				    if (a.size() == 0) {
					    while (true) {
						    RödaValue v = i.pull();
						    if (v == null) break;
						    _out.push(v);
					    }
				    }
				    else {
					    for (RödaValue v : a) {
						    _out.push(v);
					    }
				    }
			    }, Arrays.asList(new Parameter("values", false)), true);
	}

	public static RödaValue genericPull(String name, RödaStream _in, boolean peek, boolean oneOnly) {
		return RödaNativeFunction
			.of(name,
			    (ra, a, k, s, i, o) -> {
			    	if (!oneOnly) checkArgs(name, 0, a.size());
				    if (a.size() == 0) {
				    	if (oneOnly) {
				    		RödaValue v = peek ? _in.peek() : _in.pull();
						    if (v == null) error("empty stream");
						    o.push(v);
				    	}
				    	else while (true) {
					    	RödaValue v = peek ? _in.peek() : _in.pull();
						    if (v == null) break;
						    o.push(v);
					    }
				    }
				    else {
					    for (RödaValue v : a) {
						    checkReference(name, v);
						    RödaValue pulled
							    = peek ? _in.peek() : _in.pull();
						    if (pulled == null)
						    	error("empty stream");
						    v.assignLocal(pulled);
					    }
				    }
			    }, Arrays.asList(new Parameter("variables", true)), true);
	}

	public static RödaValue genericTryPull(String name, RödaStream _in, boolean peek) {
		return RödaNativeFunction
			.of(name,
			    (ra, a, k, s, i, o) -> {
				    for (RödaValue v : a) {
					    checkReference(name, v);
					    RödaValue pulled
						    = peek ? _in.peek() : _in.pull();
					    if (pulled == null) {
					    	o.push(RödaBoolean.of(false));
					    }
					    else {
					    	o.push(RödaBoolean.of(true));
						    v.assignLocal(pulled);
					    }
				    }
			    }, Arrays.asList(new Parameter("variables", true)), true);
	}

	public static RödaValue genericWrite(String name, OutputStream _out, Interpreter I) {
		return RödaNativeFunction
			.of(name,
			    (ra, args, kwargs, scope, in, out) -> {
				    try {
					    if (args.size() == 0) {
						    while (true) {
							    RödaValue v = in.pull();
							    if (v == null) break;
							    checkString(name, v);
							    _out.write(v.str().getBytes(StandardCharsets.UTF_8));
							    _out.flush();
						    }
					    }
					    else {
						    for (int i = 0; i < args.size(); i++) {
							    RödaValue v = args.get(i);
							    if (v.isFlag("-f")) {
								    RödaValue _file = args.get(++i);
								    checkString(name, _file);
								    File file = IOUtils
									    .getMaybeRelativeFile(I.currentDir,
												  _file.str());
								    try {
									    byte[] buf = new byte[2048];
									    InputStream is =
										    new FileInputStream(file);
									    int c = 0;
									    while ((c=is.read(buf, 0, buf.length))
										   > 0) {
										    _out.write(buf, 0, c);
										    _out.flush();
									    }
									    is.close();
								    } catch (IOException e) {
									    error(e);
								    }
								    
								    continue;
							    }
							    checkString(name, v);
							    _out.write(v.str().getBytes(StandardCharsets.UTF_8));
							    _out.flush();
						    }
					    }
				    } catch (IOException e) {
					    error(e);
				    }
			    }, Arrays.asList(new Parameter("values", false)), true);
	}

	public static RödaValue genericRead(String name, InputStream _in, Interpreter I) {
		return RödaNativeFunction
			.of(name,
			    (ra, args, kwargs, scope, in, out) -> {
				    try {
					    if (args.size() == 0) {
						    while (true) {
							    int i = _in.read();
							    if (i == -1) break;
							    out.push(RödaInteger.of(i));
						    }
					    }
					    else {
						    Iterator<RödaValue> it = args.iterator();
						    while (it.hasNext()) {
							    RödaValue v = it.next();
							    checkFlag(name, v);
							    String option = v.str();
							    RödaValue value;
							    switch (option) {
							    case "-b": {
								    RödaValue sizeVal = it.next().impliciteResolve();
								    checkNumber(name, sizeVal);
								    long size = sizeVal.integer();
								    if (size > Integer.MAX_VALUE)
									    error(name + ": can't read more than "
										  + Integer.MAX_VALUE + " bytes "
										  + "at time");
								    byte[] data = new byte[(int) size];
								    _in.read(data);
								    value = RödaString.of(new String(data, StandardCharsets.UTF_8));
							    } break;
							    case "-l": {
								    List<Byte> bytes = new ArrayList<>(256);
								    int i;
								    do {
									    i = _in.read();
									    if (i == -1) break;
									    bytes.add((byte) i);
								    } while (i != '\n');
								    byte[] byteArr = new byte[bytes.size()];
								    for (int j = 0; j < bytes.size(); j++) byteArr[j] = bytes.get(j);
								    value = RödaString.of(new String(byteArr, StandardCharsets.UTF_8));
							    } break;
							    default:
								    error(name + ": unknown option '" + option + "'");
								    value = null;
							    }
							    RödaValue refVal = it.next();
							    checkReference(name, refVal);
							    refVal.assign(value);
						    }
					    }
				    } catch (IOException e) {
					    error(e);
				    }
			    }, Arrays.asList(new Parameter("variables", true)), true);
	}
}
