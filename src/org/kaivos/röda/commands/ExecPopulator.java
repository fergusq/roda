package org.kaivos.röda.commands;

import static java.util.stream.Collectors.toList;
import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.Interpreter.error;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaException;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class ExecPopulator {

	private ExecPopulator() {}

	public static void populateExec(Interpreter I, RödaScope S) {
		S.setLocal("exec", RödaNativeFunction.of("exec", (typeargs, args, kwargs, scope, in, out) -> {
			HashMap<String, String> envVars = new HashMap<>();
			class C {
				boolean lineMode = false, enableInput = true;
			}
			C c = new C();
			while (args.size() > 0 && args.get(0).is(RödaValue.FLAG)) {
				RödaValue flag = args.remove(0);
				if (flag.isFlag("-E")) {
					if (args.size() < 3)
						argumentUnderflow("exec", 4, args.size() + 1);
					checkString("exec", args.get(0));
					checkString("exec", args.get(0));
					envVars.put(args.get(0).str(), args.get(1).str());
					args.remove(0);
					args.remove(0);
				} else if (flag.isFlag("-l")) {
					c.lineMode = true;
				} else if (flag.isFlag("-c")) {
					c.lineMode = false;
				} else if (flag.isFlag("-i")) {
					c.enableInput = true;
				} else if (flag.isFlag("-I")) {
					c.enableInput = false;
				}
			}
			if (args.size() < 1)
				argumentUnderflow("exec", 1, args.size());
			List<String> params = args.stream().map(v -> v.str()).collect(toList());
			try {

				ProcessBuilder b = new ProcessBuilder(params);
				b.directory(I.currentDir);
				b.environment().putAll(envVars);
				Process p = b.start();
				InputStream pout = p.getInputStream();
				PrintWriter pin = new PrintWriter(p.getOutputStream());
				Runnable input = () -> {
					if (c.enableInput) {
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
						if (c.lineMode) {
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
		}, Arrays.asList(new Parameter("flags_command_and_args", false)), true));
	}
}
