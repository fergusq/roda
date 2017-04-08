package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.RödaValue.STRING;

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
import org.kaivos.röda.Parser;
import org.kaivos.röda.Röda;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class WcatPopulator {

	private WcatPopulator() {}
	
	private static void addResourceLoader(RödaScope S, String name, boolean saveToFile) {
		S.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			try {
				String useragent = kwargs.get("ua").str();
				
				String arg = args.get(0).str();

				URL url = new URL(arg);
				URLConnection c = url.openConnection();
				if (!useragent.isEmpty())
					c.setRequestProperty("User-Agent", useragent);
				c.connect();
				InputStream input = c.getInputStream();
				if (saveToFile) {
					String outputFile = args.get(1).str();
					Files.copy(input, new File(outputFile).toPath(), StandardCopyOption.REPLACE_EXISTING);
				} else {
					for (String line : IOUtils.streamLineIterator(input)) {
						out.push(RödaString.of(line));
					}
				}
				input.close();
			} catch (MalformedURLException e) {
				error(e);
			} catch (IOException e) {
				error(e);
			}
		}, saveToFile
				? Arrays.asList(new Parameter("url", false, STRING))
				: Arrays.asList(new Parameter("url", false, STRING), new Parameter("filename", false, STRING)), true,
				Arrays.asList(new Parameter("ua", false,
						Parser.expressionString("<wcat populator>", 0, "Roeda/"+Röda.RÖDA_VERSION_STRING)))));
	}

	public static void populateWcat(RödaScope S) {
		addResourceLoader(S, "loadResourceLines", false);
		addResourceLoader(S, "saveResource", true);
	}
}
