package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.Interpreter.error;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import org.kaivos.röda.IOUtils;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class WcatPopulator {

	private WcatPopulator() {}

	public static void populateWcat(RödaScope S) {
		S.setLocal("wcat", RödaNativeFunction.of("wcat", (typeargs, args, scope, in, out) -> {
					if (args.size() < 1) argumentUnderflow("wcat", 1, args.size());
					try {
						String useragent = "";
						String outputFile = "";
						for (int i = 0; i < args.size(); i++) {
							RödaValue _arg = args.get(i);
							
							if (_arg.isFlag("-U")) {
								RödaValue _ua = args.get(++i);
								checkString("wcat", _ua);
								useragent = _ua.str();
								continue;
							}
							if (_arg.isFlag("-O")) {
								RödaValue _of = args.get(++i);
								checkString("wcat", _of);
								outputFile = _of.str();
								continue;
							}
							
							checkString("wcat", _arg);
							String arg = _arg.str();
							
							URL url = new URL(arg);
							URLConnection c = url.openConnection();
							if (!useragent.isEmpty())
								c.setRequestProperty("User-Agent" , useragent);
							c.connect();
							InputStream input = c.getInputStream();
							if (!outputFile.isEmpty()) {
								Files.copy(input, new File(outputFile).toPath(), StandardCopyOption.REPLACE_EXISTING);
							}
							else {
								for (String line : IOUtils.streamLineIterator(input)) {
									out.push(RödaString.of(line));
								}
							}
							input.close();
						}
					} catch (MalformedURLException e) {
						error(e);
					} catch (IOException e) {
						error(e);
					}
				}, Arrays.asList(new Parameter("urls", false)), true));
	}
}
