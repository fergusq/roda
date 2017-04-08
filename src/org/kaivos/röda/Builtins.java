package org.kaivos.röda;

import static org.kaivos.röda.Interpreter.argumentOverflow;
import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkArgs;
import static org.kaivos.röda.Interpreter.checkInteger;
import static org.kaivos.röda.Interpreter.checkReference;
import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.Interpreter.emptyStream;
import static org.kaivos.röda.Interpreter.error;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.commands.AssignGlobalPopulator;
import org.kaivos.röda.commands.BtosAndStobPopulator;
import org.kaivos.röda.commands.CasePopulator;
import org.kaivos.röda.commands.CdAndPwdPopulator;
import org.kaivos.röda.commands.ChrAndOrdPopulator;
import org.kaivos.röda.commands.CurrentTimePopulator;
import org.kaivos.röda.commands.EnumPopulator;
import org.kaivos.röda.commands.ErrorPopulator;
import org.kaivos.röda.commands.ErrprintPopulator;
import org.kaivos.röda.commands.ExecPopulator;
import org.kaivos.röda.commands.FilePopulator;
import org.kaivos.röda.commands.FilterPopulator;
import org.kaivos.röda.commands.GetenvPopulator;
import org.kaivos.röda.commands.HeadAndTailPopulator;
import org.kaivos.röda.commands.IdentityPopulator;
import org.kaivos.röda.commands.ImportPopulator;
import org.kaivos.röda.commands.IndexOfPopulator;
import org.kaivos.röda.commands.InterleavePopulator;
import org.kaivos.röda.commands.JsonPopulator;
import org.kaivos.röda.commands.KeysPopulator;
import org.kaivos.röda.commands.MatchPopulator;
import org.kaivos.röda.commands.MathPopulator;
import org.kaivos.röda.commands.NamePopulator;
import org.kaivos.röda.commands.ParseNumPopulator;
import org.kaivos.röda.commands.PushAndPullPopulator;
import org.kaivos.röda.commands.RandomPopulator;
import org.kaivos.röda.commands.ReadAndWritePopulator;
import org.kaivos.röda.commands.ReplacePopulator;
import org.kaivos.röda.commands.SearchPopulator;
import org.kaivos.röda.commands.SeqPopulator;
import org.kaivos.röda.commands.ServerPopulator;
import org.kaivos.röda.commands.ShiftPopulator;
import org.kaivos.röda.commands.SortPopulator;
import org.kaivos.röda.commands.SplitPopulator;
import org.kaivos.röda.commands.StreamPopulator;
import org.kaivos.röda.commands.StrsizePopulator;
import org.kaivos.röda.commands.ReducePopulator;
import org.kaivos.röda.commands.ThreadPopulator;
import org.kaivos.röda.commands.TrueAndFalsePopulator;
import org.kaivos.röda.commands.UndefinePopulator;
import org.kaivos.röda.commands.UniqPopulator;
import org.kaivos.röda.commands.WcatPopulator;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaBoolean;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaList;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public class Builtins {

	private Builtins() {}

	static void populate(Interpreter I) {
		RödaScope S = I.G;

		/* Perusvirtaoperaatiot */
		
		if (I.enableProfiling) I.pushTimer();

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
		
		if (I.enableProfiling) I.popTimer("<populate basic stream operations>");

		/* Muuttujaoperaatiot */
		
		if (I.enableProfiling) I.pushTimer();
		UndefinePopulator.populateUndefine(S);
		NamePopulator.populateName(S);
		ImportPopulator.populateImport(I, S);
		AssignGlobalPopulator.populateAssignGlobal(S);
		if (I.enableProfiling) I.popTimer("<populate variable operations>");

		/* Muut oleelliset kielen rakenteet */
		
		if (I.enableProfiling) I.pushTimer();
		ErrorPopulator.populateError(S);
		ErrprintPopulator.populateErrprint(S);
		if (I.enableProfiling) I.popTimer("<populate error functions>");

		/* Täydentävät virtaoperaatiot */

		if (I.enableProfiling) I.pushTimer();
		IdentityPopulator.populateIdentity(S);
		HeadAndTailPopulator.populateHeadAndTail(S);
		InterleavePopulator.populateInterleave(S);
		SortPopulator.populateSort(I, S);
		UniqPopulator.populateUniq(S);
		ReducePopulator.populateReduce(S);
		FilterPopulator.populateFilterAndGrep(I, S);
		EnumPopulator.populateEnum(S);
		if (I.enableProfiling) I.popTimer("<populate other stream operations>");

		/* Yksinkertaiset merkkijonopohjaiset virtaoperaatiot */

		if (I.enableProfiling) I.pushTimer();
		SearchPopulator.populateSearch(S);
		MatchPopulator.populateMatch(S);
		ReplacePopulator.populateReplace(S);

		/* Parserit */

		SplitPopulator.populateSplit(S);
		JsonPopulator.populateJson(S);
		ParseNumPopulator.populateParseNum(S);
		BtosAndStobPopulator.populateBtosAndStob(S);
		StrsizePopulator.populateStrsize(S);
		ChrAndOrdPopulator.populateChrAndOrd(S);
		CasePopulator.populateUpperAndLowerCase(S);
		if (I.enableProfiling) I.popTimer("<populate string-related functions>");

		/* Konstruktorit */

		if (I.enableProfiling) I.pushTimer();
		SeqPopulator.populateSeq(S);
		TrueAndFalsePopulator.populateTrueAndFalse(S);
		StreamPopulator.populateStream(I, S);
		if (I.enableProfiling) I.popTimer("<populate value constructors>");

		/* Merkkijono-, lista- ja karttaoperaatiot */

		if (I.enableProfiling) I.pushTimer();
		ShiftPopulator.populateShift(S);
		KeysPopulator.populateKeys(S);
		IndexOfPopulator.populateIndexOf(S);
		if (I.enableProfiling) I.popTimer("<populate list operations>");
		
		/* Apuoperaatiot */

		if (I.enableProfiling) I.pushTimer();
		CurrentTimePopulator.populateTime(S);
		RandomPopulator.populateRandom(S);
		ExecPopulator.populateExec(I, S);
		GetenvPopulator.populateGetenv(S);
		MathPopulator.populateMath(S);
		if (I.enableProfiling) I.popTimer("<populate utilities>");

		/* Tiedosto-operaatiot */

		if (I.enableProfiling) I.pushTimer();
		CdAndPwdPopulator.populateCdAndPwd(I, S);
		ReadAndWritePopulator.populateReadAndWrite(I, S);
		FilePopulator.populateFile(I, S);
		if (I.enableProfiling) I.popTimer("<populate file operations>");

		/* Verkko-operaatiot */

		if (I.enableProfiling) I.pushTimer();
		WcatPopulator.populateWcat(S);
		ServerPopulator.populateServer(I, S);
		if (I.enableProfiling) I.popTimer("<populate network operations>");

		// Säikeet

		if (I.enableProfiling) I.pushTimer();
		ThreadPopulator.populateThread(I, S);
		if (I.enableProfiling) I.popTimer("<populate thread operations>");
	}

	public static RödaValue genericPush(String name, RödaStream _out, boolean back) {
		return RödaNativeFunction.of(name, (ra, a, k, s, i, o) -> {
			if (a.size() == 0) {
				while (true) {
					RödaValue v = i.pull();
					if (v == null) break;
					if (back) _out.unpull(v);
					else _out.push(v);
				}
			} else {
				for (RödaValue v : a) {
					if (back) _out.unpull(v);
					else _out.push(v);
				}
			}
		}, Arrays.asList(new Parameter("values", false)), true);
	}

	public static RödaValue genericPull(String name, RödaStream _in, boolean peek, boolean oneOnly) {
		return RödaNativeFunction.of(name, (ra, a, k, s, i, o) -> {
			if (!oneOnly) checkArgs(name, 0, a.size());
			if (a.size() == 0) {
				if (oneOnly) {
					RödaValue v = peek ? _in.peek() : _in.pull();
					if (v == null) emptyStream("empty stream");
					o.push(v);
				} else
					while (true) {
						RödaValue v = peek ? _in.peek() : _in.pull();
						if (v == null) break;
						o.push(v);
					}
			} else {
				for (RödaValue v : a) {
					checkReference(name, v);
					RödaValue pulled = peek ? _in.peek() : _in.pull();
					if (pulled == null) emptyStream("empty stream");
					v.assignLocal(pulled);
				}
			}
		}, Arrays.asList(new Parameter("variables", true)), true);
	}

	public static RödaValue genericTryPull(String name, RödaStream _in, boolean peek) {
		return RödaNativeFunction.of(name, (ra, a, k, s, i, o) -> {
			for (RödaValue v : a) {
				checkReference(name, v);
				RödaValue pulled = peek ? _in.peek() : _in.pull();
				if (pulled == null) {
					o.push(RödaBoolean.of(false));
				} else {
					o.push(RödaBoolean.of(true));
					v.assignLocal(pulled);
				}
			}
		}, Arrays.asList(new Parameter("variables", true)), true);
	}

	public static RödaValue genericWriteStrings(String name, OutputStream _out, Interpreter I) {
		return RödaNativeFunction.of(name, (ra, args, kwargs, scope, in, out) -> {
			Consumer<Consumer<RödaValue>> forAll;
			if (args.size() == 0) {
				forAll = in::forAll;
			} else {
				forAll = args.stream()::forEach;
			}
			forAll.accept(v -> {
				try {
					checkString(name, v);
					_out.write(v.str().getBytes(StandardCharsets.UTF_8));
					_out.flush();
				} catch (IOException e) {
					error(e);
				}
			});
		}, Arrays.asList(new Parameter("values", false)), true);
	}
	
	public static RödaValue genericWriteFile(String name, OutputStream _out, Interpreter I) {
		return RödaNativeFunction.of(name, (ra, args, kwargs, scope, in, out) -> {
			RödaValue _file = args.get(0);
			checkString(name, _file);
			File file = IOUtils.getMaybeRelativeFile(I.currentDir, _file.str());
			try {
				byte[] buf = new byte[2048];
				InputStream is = new FileInputStream(file);
				int c = 0;
				while ((c = is.read(buf, 0, buf.length)) > 0) {
					_out.write(buf, 0, c);
					_out.flush();
				}
				is.close();
			} catch (IOException e) {
				error(e);
			}
		}, Arrays.asList(new Parameter("file", false, RödaValue.STRING)), false);
	}

	public static RödaValue genericReadBytesOrString(String name, InputStream _in, Interpreter I, boolean toString) {
		return RödaNativeFunction.of(name, (ra, args, kwargs, scope, in, out) -> {
			try {
				RödaValue sizeVal = args.get(0);
				checkInteger(name, sizeVal);
				long size = sizeVal.integer();
				if (size > Integer.MAX_VALUE)
					error(name + ": can't read more than " + Integer.MAX_VALUE + " bytes " + "at time");
				byte[] data = new byte[(int) size];
				_in.read(data);
				RödaValue output;
				if (toString) {
					output = RödaString.of(new String(data, StandardCharsets.UTF_8));
				} else {
					List<RödaValue> list = new ArrayList<>();
					for (byte b : data) {
						list.add(RödaInteger.of(b));
					}
					output = RödaList.of(list);
				}
				if (args.size() == 1) {
					out.push(output);
				} else if (args.size() == 2) {
					args.get(1).assignLocal(output);
				} else {
					argumentOverflow(name, 2, args.size());
				}
			} catch (IOException e) {
				error(e);
			}
		}, Arrays.asList(new Parameter("number_of_bytes", false, RödaValue.INTEGER), new Parameter("variable", true)), true);
	}
	
	private static RödaValue readLine(InputStream in) throws IOException {
		List<Byte> bytes = new ArrayList<>(256);
		int i;
		do {
			i = in.read();
			if (i == -1)
				break;
			bytes.add((byte) i);
		} while (i != '\n');
		byte[] byteArr = new byte[bytes.size()];
		for (int j = 0; j < bytes.size(); j++)
			byteArr[j] = bytes.get(j);
		return RödaString.of(new String(byteArr, StandardCharsets.UTF_8));
	}

	public static RödaValue genericReadLine(String name, InputStream _in, Interpreter I) {
		return RödaNativeFunction.of(name, (ra, args, kwargs, scope, in, out) -> {
			try {
				if (args.isEmpty()) {
					out.push(readLine(_in));
				}
				else for (RödaValue refVal : args) {
					checkReference(name, refVal);
					refVal.assignLocal(readLine(_in));
				}
			} catch (IOException e) {
				error(e);
			}
		}, Arrays.asList(new Parameter("variables", true)), true);
	}
}
