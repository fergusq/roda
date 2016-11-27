package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkArgs;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.RödaValue.INTEGER;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;

import org.kaivos.röda.Builtins;
import org.kaivos.röda.Datatype;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.Parser.Parameter;
import org.kaivos.röda.Parser.Record;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaRecordInstance;
import org.kaivos.röda.type.RödaString;

public final class ServerPopulator {

	private ServerPopulator() {
	}

	public static void populateServer(Interpreter I, RödaScope S) {
		Record serverRecord = new Record("Server", Collections.emptyList(), Collections.emptyList(),
				Arrays.asList(new Record.Field("accept", new Datatype("function")),
						new Record.Field("close", new Datatype("function"))),
				false);
		I.registerRecord(serverRecord);

		Record socketRecord = new Record("Socket", Collections.emptyList(), Collections.emptyList(), Arrays.asList(
				new Record.Field("write", new Datatype("function")), new Record.Field("read", new Datatype("function")),
				new Record.Field("close", new Datatype("function")), new Record.Field("ip", new Datatype("string")),
				new Record.Field("hostname", new Datatype("string")), new Record.Field("port", new Datatype("number")),
				new Record.Field("localport", new Datatype("number"))), false);
		I.registerRecord(socketRecord);

		S.setLocal("server", RödaNativeFunction.of("server", (typeargs, args, kwargs, scope, in, out) -> {
			long port = args.get(0).integer();
			if (port > Integer.MAX_VALUE)
				error("can't open port greater than " + Integer.MAX_VALUE);

			try {

				ServerSocket server = new ServerSocket((int) port);

				RödaValue serverObject = RödaRecordInstance.of(serverRecord, Collections.emptyList(), I.records);
				serverObject.setField("accept", RödaNativeFunction.of("Server.accept", (ra, a, k, s, i, o) -> {
					checkArgs("Server.accept", 0, a.size());
					Socket socket;
					InputStream _in;
					OutputStream _out;
					try {
						socket = server.accept();
						_in = socket.getInputStream();
						_out = socket.getOutputStream();
					} catch (IOException e) {
						error(e);
						return;
					}
					RödaValue socketObject = RödaRecordInstance.of(socketRecord, Collections.emptyList(), I.records);
					socketObject.setField("read", Builtins.genericRead("Socket.read", _in, I));
					socketObject.setField("write", Builtins.genericWrite("Socket.write", _out, I));
					socketObject.setField("close", RödaNativeFunction.of("Socket.close", (r, A, K, z, j, u) -> {
						checkArgs("Socket.close", 0, A.size());
						try {
							_out.close();
							_in.close();
							socket.close();
						} catch (IOException e) {
							error(e);
						}
					}, Collections.emptyList(), false));
					socketObject.setField("ip", RödaString.of(socket.getInetAddress().getHostAddress()));
					socketObject.setField("hostname", RödaString.of(socket.getInetAddress().getCanonicalHostName()));
					socketObject.setField("port", RödaInteger.of(socket.getPort()));
					socketObject.setField("localport", RödaInteger.of(socket.getLocalPort()));
					o.push(socketObject);
				}, Collections.emptyList(), false));
				serverObject.setField("close", RödaNativeFunction.of("Server.close", (ra, a, k, s, i, o) -> {
					checkArgs("Server.close", 0, a.size());
					try {
						server.close();
					} catch (Exception e) {
						error(e);
					}
				}, Collections.emptyList(), false));
				out.push(serverObject);
			} catch (IOException e) {
				error(e);
			}
		}, Arrays.asList(new Parameter("port", false, INTEGER)), false));
	}
}
