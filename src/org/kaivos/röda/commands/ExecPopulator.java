package org.kaivos.röda.commands;

import static java.util.stream.Collectors.toList;
import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.Interpreter.checkMap;
import static org.kaivos.röda.Interpreter.error;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.kaivos.röda.Datatype;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaException;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class ExecPopulator {

	private ExecPopulator() {}
	
	public static void addExecFunction(Interpreter I, String name, boolean lineMode) {
		I.G.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			if (args.size() < 1)
				argumentUnderflow("exec", 1, args.size());
			List<String> params = args.stream().map(v -> v.str()).collect(toList());
			
			HashMap<String, String> envVars = new HashMap<>();
			checkMap("exec", kwargs.get("env"));
			for (Entry<String, RödaValue> e : kwargs.get("env").map().entrySet()) {
				checkString("exec", e.getValue());
				envVars.put(e.getKey(), e.getValue().str());
			}
			
			try {
				ProcessBuilder b = new ProcessBuilder(params);
				b.directory(I.currentDir);
				b.environment().putAll(envVars);
				Process p = b.start();
				InputStream pout = p.getInputStream();
				PrintWriter pin = new PrintWriter(p.getOutputStream());
				Runnable input = () -> {
					if (true) {
						while (true) {
							RödaValue value = in.pull();
							if (value == null)
								break;
							pin.print(value.str());
							pin.flush();
						}
					}
					pin.close();
				};
				Runnable output = () -> {
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
				};
				Future<?> futureIn = Interpreter.executor.submit(input);
				Future<?> futureOut = Interpreter.executor.submit(output);
				futureOut.get();
				in.pause();
				futureIn.get();
				in.unpause();
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
						Parser.expressionNew("<exec populator>", -1, new Datatype("map"), Collections.emptyList())))));
	}

	public static void populateExec(Interpreter I, RödaScope S) {
		addExecFunction(I, "exec", false);
		addExecFunction(I, "execL", true);
	}
}
