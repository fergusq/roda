package org.kaivos.röda.commands;

import static org.kaivos.röda.Interpreter.checkArgs;
import static org.kaivos.röda.Interpreter.error;
import static org.kaivos.röda.Interpreter.outOfBounds;
import static org.kaivos.röda.RödaValue.INTEGER;
import static org.kaivos.röda.RödaValue.STRING;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;

import org.kaivos.röda.Builtins;
import org.kaivos.röda.Interpreter;
import org.kaivos.röda.Interpreter.RödaScope;
import org.kaivos.röda.RödaValue;
import org.kaivos.röda.runtime.Function.Parameter;
import org.kaivos.röda.runtime.Datatype;
import org.kaivos.röda.runtime.Record;
import org.kaivos.röda.type.RödaInteger;
import org.kaivos.röda.type.RödaNativeFunction;
import org.kaivos.röda.type.RödaRecordInstance;
import org.kaivos.röda.type.RödaString;

public final class ServerPopulator {

	private ServerPopulator() {
	}
	
	private static Record socketRecord;
	
	private static RödaValue createSocketObj(Socket socket, Interpreter I) {
		InputStream _in;
		OutputStream _out;
		try {
			_in = socket.getInputStream();
			_out = socket.getOutputStream();
		} catch (IOException e) {
			error(e);
			return null;
		}
		RödaValue socketObject = RödaRecordInstance.of(socketRecord, Collections.emptyList());
		socketObject.setField("readBytes", Builtins.genericReadBytesOrString("Socket.readBytes", _in, I, false));
		socketObject.setField("readString", Builtins.genericReadBytesOrString("Socket.readString", _in, I, true));
		socketObject.setField("readLine", Builtins.genericReadLine("Socket.readLine", _in, I));
		socketObject.setField("writeStrings", Builtins.genericWriteStrings("Socket.writeStrings", _out, I));
		socketObject.setField("writeFile", Builtins.genericWriteFile("Socket.writeFile", _out, I));
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
		return socketObject;
	}

	public static void populateServer(Interpreter I, RödaScope S) {
		Record serverRecord = new Record("Server", Collections.emptyList(), Collections.emptyList(),
				Arrays.asList(new Record.Field("accept", new Datatype("function")),
						new Record.Field("close", new Datatype("function"))),
				false, I.G);
		I.G.preRegisterRecord(serverRecord);

		socketRecord = new Record("Socket", Collections.emptyList(), Collections.emptyList(), Arrays.asList(
				new Record.Field("writeStrings", new Datatype("function")),
				new Record.Field("writeFile", new Datatype("function")),
				new Record.Field("readBytes", new Datatype("function")),
				new Record.Field("readString", new Datatype("function")),
				new Record.Field("readLine", new Datatype("function")),
				new Record.Field("close", new Datatype("function")), new Record.Field("ip", new Datatype("string")),
				new Record.Field("hostname", new Datatype("string")), new Record.Field("port", new Datatype("number")),
				new Record.Field("localport", new Datatype("number"))), false, I.G);
		I.G.preRegisterRecord(socketRecord);
		
		I.G.postRegisterRecord(serverRecord);
		I.G.postRegisterRecord(socketRecord);

		S.setLocal("server", RödaNativeFunction.of("server", (typeargs, args, kwargs, scope, in, out) -> {
			long port = args.get(0).integer();
			if (port > Integer.MAX_VALUE)
				outOfBounds("can't open port greater than " + Integer.MAX_VALUE);
			if (port < 0)
				outOfBounds("can't open port less than 0");

			try {

				ServerSocket server = new ServerSocket((int) port);

				RödaValue serverObject = RödaRecordInstance.of(serverRecord, Collections.emptyList());
				serverObject.setField("accept", RödaNativeFunction.of("Server.accept", (ra, a, k, s, i, o) -> {
					checkArgs("Server.accept", 0, a.size());
					Socket socket;
					try {
						socket = server.accept();
					} catch (IOException e) {
						error(e);
						return;
					}
					o.push(createSocketObj(socket, I));
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
		
		S.setLocal("connect", RödaNativeFunction.of("connect", (typeargs, args, kwargs, scope, in, out) -> {
			String host = args.get(0).str();
			long port = args.get(1).integer();
			if (port > Integer.MAX_VALUE)
				outOfBounds("can't open port greater than " + Integer.MAX_VALUE);
			if (port < 0)
				outOfBounds("can't open port less than 0");
			
			try {
				Socket socket = new Socket(host, (int) port);
				out.push(createSocketObj(socket, I));
			} catch (IOException e) {
				error(e);
			}
		}, Arrays.asList(new Parameter("host", false, STRING), new Parameter("port", false, INTEGER)), false));
	}
}
