package org.kaivos.röda.commands;

import static java.util.stream.Collectors.toList;
import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkMap;
import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.Interpreter.error;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaException;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser;
import org.kaivos.röda.Parser.DatatypeTree;
import org.kaivos.röda.RödaStream;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class ExecPopulator {

	private ExecPopulator() {}
	
	private static void outputThread(InputStream pout, RödaStream out, boolean lineMode) {
		InputStreamReader reader = new InputStreamReader(pout);
		try {
			if (lineMode) {
				BufferedReader br = new BufferedReader(reader);
				while (true) {
					String str = br.readLine();
					if (str == null)
						break;
					out.push(RödaString.of(str));
				}
				br.close();
			} else {
				while (true) {
					int chr = reader.read();
					if (chr == -1)
						break;
					out.push(RödaString.of(String.valueOf((char) chr)));
				}
			}
			reader.close();
		} catch (IOException e) {
			error(e);
		}
	}
	
	public static void addExecFunction(Interpreter I, String name, boolean lineMode) {
		I.G.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() < 1)
				argumentUnderflow(name, 1, args.size());
			List<String> params = args.stream().map(v -> v.str()).collect(toList());
			
			HashMap<String, String> envVars = new HashMap<>();
			checkMap(name, kwargs.get("env"));
			for (Entry<String, RödaValue> e : kwargs.get("env").map().entrySet()) {
				checkString(name, e.getValue());
				envVars.put(e.getKey(), e.getValue().str());
			}
			
			boolean inheritIn = false, inheritOut = false, inheritErr = true;
			
			if (kwargs.containsKey("redirect_in")) {
				RödaValue val = kwargs.get("redirect_in");
				if (val.is(RödaValue.BOOLEAN)) inheritIn = val.bool();
			}
			
			if (kwargs.containsKey("redirect_out")) {
				RödaValue val = kwargs.get("redirect_out");
				if (val.is(RödaValue.BOOLEAN)) inheritOut = val.bool();
			}
			
			if (kwargs.containsKey("redirect_err")) {
				RödaValue val = kwargs.get("redirect_err");
				if (val.is(RödaValue.BOOLEAN)) inheritErr = val.bool();
			}
			
			try {
				ProcessBuilder b = new ProcessBuilder(params);
				if (inheritIn) b.redirectInput(Redirect.INHERIT);
				if (inheritOut) b.redirectOutput(Redirect.INHERIT);
				if (inheritErr) b.redirectError(Redirect.INHERIT);
				b.directory(I.currentDir);
				b.environment().putAll(envVars);
				Process p = b.start();
				InputStream pout = p.getInputStream();
				InputStream perr = p.getErrorStream();
				PrintWriter pin = new PrintWriter(p.getOutputStream());
				Runnable input = () -> {
					if (true) {
						while (p.isAlive()) {
							RödaValue value = in.pull();
							if (value == null)
								break;
							pin.print(value.str());
							pin.flush();
						}
					}
					pin.close();
				};
				Runnable output = () -> outputThread(pout, out, lineMode);
				Runnable errput = () -> outputThread(perr, out, lineMode);
				Future<?> futureIn = null, futureOut = null, futureErr = null;
				if (!inheritIn) futureIn = Interpreter.executor.submit(input);
				if (!inheritOut) futureOut = Interpreter.executor.submit(output);
				if (!inheritErr) futureErr = Interpreter.executor.submit(errput);
				if (!inheritOut) futureOut.get();
				if (!inheritErr) futureErr.get();
				if (!inheritIn) futureIn.get();
				p.waitFor();
			} catch (IOException e) {
				error(e);
			} catch (InterruptedException e) {
				error(e);
			} catch (ExecutionException e) {
				if (e.getCause() instanceof RödaException) {
					throw (RödaException) e.getCause();
				}
				error(e.getCause());
			}
		}, Arrays.asList(new Parameter("command", false), new Parameter("args", false)), true,
				Arrays.asList(new Parameter("env", false,
						Parser.expressionNew("<exec populator>", -1, new DatatypeTree("map"), Collections.emptyList()))),
				true));
	}

	public static void populateExec(Interpreter I, RödaScope S) {
		addExecFunction(I, "exec", false);
		addExecFunction(I, "bufferedExec", true);
	}
}
