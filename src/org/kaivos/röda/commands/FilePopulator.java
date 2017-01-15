package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.argumentUnderflow;
import static org.kaivos.röda.Interpreter.checkString;
import static org.kaivos.röda.Interpreter.error;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;
import java.util.function.BiConsumer;

import org.kaivos.röda.IOUtils;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaStream;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.type.RödaBoolean;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaString;

public final class FilePopulator {

	private FilePopulator() {}
	
	private static void processQuery(Interpreter I, String name, RödaStream out, BiConsumer<File, RödaStream> consumer, RödaValue value) {
		checkString(name, value);
		String filename = value.str();
		File file = IOUtils.getMaybeRelativeFile(I.currentDir, filename);
		consumer.accept(file, out);
	}
	
	private static void addQueryType(Interpreter I, String name, BiConsumer<File, RödaStream> consumer) {
		I.G.setLocal(name, RödaNativeFunction.of(name, (typeargs, args, kwargs, scope, in, out) -> {
			if (args.isEmpty()) {
				argumentUnderflow(name, 1, 0);
			}
			else {
				for (int i = 0; i < args.size(); i++) {
					RödaValue value = args.get(i);
					processQuery(I, name, out, consumer, value);
				}
			}
		}, Arrays.asList(new Parameter("files", false)), true));
	}

	public static void populateFile(Interpreter I, RödaScope S) {
		addQueryType(I, "fileLength", (file, out) -> out.push(RödaInteger.of(file.length())));
		addQueryType(I, "fileExists", (file, out) -> out.push(RödaBoolean.of(file.exists())));
		addQueryType(I, "isFile", (file, out) -> out.push(RödaBoolean.of(file.isFile())));
		addQueryType(I, "isDirectory", (file, out) -> out.push(RödaBoolean.of(file.isDirectory())));
		addQueryType(I, "mimeType", (file, out) -> {
			try {
				out.push(RödaString.of(Files.probeContentType(file.toPath())));
			} catch (IOException e) {
				error(e);
			}
		});
		addQueryType(I, "filePermissions", (file, out) ->{
			try {
				out.push(RödaInteger.of(permissionsToInt(Files.getPosixFilePermissions(file.toPath()))));
			} catch (IOException e) {
				error(e);
			}
		});
		addQueryType(I, "ls", (file, out) -> {
			try {
				Files.list(file.toPath()).map(p -> RödaString.of(p.toAbsolutePath().toString())).forEach(out::push);
			} catch (IOException e) {
				error(e);
			}
		});
	}

	private static int permissionsToInt(Set<PosixFilePermission> permissions) {
		return permissions.stream().mapToInt(FilePopulator::permissionToInt).sum();
	}
	
	private static int permissionToInt(PosixFilePermission permission) {
		switch (permission) {
		case OWNER_EXECUTE:
			return 100;
		case GROUP_EXECUTE:
			return 10;
		case OTHERS_EXECUTE:
			return 1;
		case OWNER_WRITE:
			return 200;
		case GROUP_WRITE:
			return 20;
		case OTHERS_WRITE:
			return 2;
		case OWNER_READ:
			return 400;
		case GROUP_READ:
			return 40;
		case OTHERS_READ:
			return 4;
		default:
			return 0;
		}
	}
}
