package org.kaivos.röda;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import java.util.function.Consumer;
import java.util.function.Supplier;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.IOException;

import org.kaivos.röda.RödaValue;
import static org.kaivos.röda.RödaValue.*;

import static org.kaivos.röda.Interpreter.error;

/**
 * RödaStream represents a pipe and can be used to transfer values from one thread
 * to another.
 *
 * Futhermore, RödaStream is a collection that holds all the current and future values in a pipe.
 * It can be used to iterate over all these values.
 */
public abstract class RödaStream implements Iterable<RödaValue> {
	StreamHandler inHandler;
	StreamHandler outHandler;
	private boolean paused = false;
	
	protected abstract RödaValue get();
	protected abstract void put(RödaValue value);

	/**
	 * Closes the stream permanently.
	 */
	public abstract void finish();

	/**
	 * Returns false if it is possible to pull values
	 * from the stream.
	 */
	public boolean closed() {
		return paused() || finished();
	}

	/**
	 * Returns true if the stream is permanently finished.
	 */
	public abstract boolean finished();
	
	/**
	 * Closes the stream so that it can be opened again.
	 */
	public void pause() {
		paused = true;
	}

	/**
	 * Opens the stream after it has been paused.
	 */
	public void unpause() {
		paused = false;
	}

	/**
	 * Returns true if the stream is paused.
	 */
	public boolean paused() {
		return paused;
	}

	/**
	 * Pushes a new value to the stream.
	 */
	public final void push(RödaValue value) {
		inHandler.handlePush(this::finished, this::put, value);
	}

	/**
	 * Pulls a value from the stream.
	 *
	 * @return the value, or null if the stream is closed.
	 */
	public final RödaValue pull() {
		return outHandler.handlePull(this::finished, this::get);
	}

	/**
	 * Returns a value that represents all current and future values in the stream.
	 */
	public final RödaValue readAll() {
		return outHandler.handleReadAll(this::finished, this::get);
	}

	/**
	 * Returns a iterator that iterates over all the current and future values in the stream.
	 */
	@Override
	public Iterator<RödaValue> iterator() {
		return new Iterator<RödaValue>() {
			RödaValue buffer;
			{
				buffer = pull();
			}
			
			@Override public boolean hasNext() {
				return buffer != null;
			}
			
			@Override public RödaValue next() {
				RödaValue tmp = buffer;
				buffer = pull();
				return tmp;
			}
		};
	}

	public static RödaStream makeStream(StreamType in, StreamType out) {
		RödaStream stream = new RödaStreamImpl();
		stream.inHandler = in.newHandler();
		stream.outHandler = out.newHandler();
		return stream;
	}

	public static RödaStream makeStream(StreamHandler in, StreamHandler out) {
		RödaStream stream = new RödaStreamImpl();
		stream.inHandler = in;
		stream.outHandler = out;
		return stream;
	}

	public static RödaStream makeStream() {
		RödaStream stream = new RödaStreamImpl();
		stream.inHandler = ValueStream.HANDLER;
		stream.outHandler = ValueStream.HANDLER;
		return stream;
	}

	public static RödaStream makeStream(Consumer<RödaValue> put, Supplier<RödaValue> get, Runnable finish, Supplier<Boolean> finished) {
		RödaStream stream = new RödaStream() {
				@Override
				public void put(RödaValue value) {
					put.accept(value);
				}

				@Override
				public RödaValue get() {
					return get.get();
				}

				boolean hasFinished = false;
				
				@Override
				public void finish() {
					hasFinished = true;
					finish.run();
				}

				@Override
				public boolean finished() {
					return hasFinished || finished.get();
				}
			};
		stream.inHandler = ValueStream.HANDLER;
		stream.outHandler = ValueStream.HANDLER;
		return stream;
	}

	public static abstract class StreamType {
		abstract StreamHandler newHandler();
	}
	private interface StreamHandler {
		void handlePush(Supplier<Boolean> finished, Consumer<RödaValue> put, RödaValue value);
		RödaValue handlePull(Supplier<Boolean> finished, Supplier<RödaValue> get);
		RödaValue handleReadAll(Supplier<Boolean> finished, Supplier<RödaValue> get);
	}
	public static class LineStream extends StreamType {
		String sep;
		LineStream() { sep="\n"; }
		LineStream(String sep) { this.sep=sep; }
		StreamHandler newHandler() {
			return new StreamHandler() {
				StringBuffer buffer = new StringBuffer();
				RödaValue valueBuffer;
				public void handlePush(Supplier<Boolean> finished,
						       Consumer<RödaValue> put,
						       RödaValue value) {
					if (!value.isString()) {
						put.accept(value);
						return;
					}
					for (String line : value.str().split(sep)) {
						put.accept(valueFromString(line));
					}
				}
				public RödaValue handlePull(Supplier<Boolean> finished,
							    Supplier<RödaValue> get) {
					if (valueBuffer != null) {
						RödaValue val = valueBuffer;
						valueBuffer = null;
						return val;
					}
					do {
						RödaValue val = get.get();
						if (!val.isString()) {
							if (buffer.length() != 0) {
								valueBuffer = val;
								return valueFromString(buffer.toString());
							}
							return val;
						}
						String str = val.str();
						if (str.indexOf(sep) >= 0) {
							String ans = str.substring(0, str.indexOf(sep));
							ans = buffer.toString() + ans;
							buffer = new StringBuffer(str.substring(str.indexOf(sep)+1));
							return valueFromString(ans);
						}
						buffer = buffer.append(str);
					} while (true);
				}
				public RödaValue handleReadAll(Supplier<Boolean> finished,
							Supplier<RödaValue> get) {
					StringBuilder all = new StringBuilder();
					while (!finished.get()) {
						RödaValue val = get.get();
						if (val == null) break;
						all = all.append(val.str());
					}
					return valueFromString(all.toString());
				}
				
			};
		}
	}
	public static class VoidStream extends StreamType {

		static final StreamHandler HANDLER = new StreamHandler() {
				public void handlePush(Supplier<Boolean> finished,
						       Consumer<RödaValue> put,
						       RödaValue value) {
					// nop
				}
				public RödaValue handlePull(Supplier<Boolean> finished,
							    Supplier<RödaValue> get) {
					error("can't pull from a void stream");
					return null;
				}
				public RödaValue handleReadAll(Supplier<Boolean> finished,
							       Supplier<RödaValue> get) {
					error("can't pull from a void stream");
					return null;
				}
			};
		StreamHandler newHandler() {
			return HANDLER;
		}

		public static final RödaStream STREAM = makeStream(HANDLER, HANDLER);
	}
	public static class ByteStream extends StreamType {
		StreamHandler newHandler() {
			return new StreamHandler() {
				List<Character> queue = new ArrayList<>();
				public void handlePush(Supplier<Boolean> finished,
						       Consumer<RödaValue> put,
						       RödaValue value) {
					if (!value.isString()) {
						put.accept(value);
						return;
					}
					for (char chr : value.str().toCharArray())
						put.accept(valueFromString(String.valueOf(chr)));
				}
				public RödaValue handlePull(Supplier<Boolean> finished,
							    Supplier<RödaValue> get) {
					if (queue.isEmpty()) {
						RödaValue val = get.get();
						if (!val.isString()) return val;
						for (char chr : val.str().toCharArray())
							queue.add(chr);
					}
					return valueFromString(String.valueOf(queue.remove(0)));
				}
				public RödaValue handleReadAll(Supplier<Boolean> finished,
							       Supplier<RödaValue> get) {
					StringBuilder all = new StringBuilder();
					while (!finished.get()) {
						RödaValue val = get.get();
						if (val == null) break;
						all = all.append(val.str());
					}
					return valueFromString(all.toString());
				}
			};
		}
	}
	public static class BooleanStream extends StreamType {
		StreamHandler newHandler() {
			return new StreamHandler() {
				public void handlePush(Supplier<Boolean> finished,
						       Consumer<RödaValue> put,
						       RödaValue value) {
					put.accept(value);
				}
				public RödaValue handlePull(Supplier<Boolean> finished,
							    Supplier<RödaValue> get) {
					RödaValue value = get.get();
					return valueFromBoolean(value.bool());
				}
				public RödaValue handleReadAll(Supplier<Boolean> finished,
							       Supplier<RödaValue> get) {
					boolean all = true;
					while (!finished.get()) {
						RödaValue val = get.get();
						if (val == null) break;
						all &= val.bool();
					}
					return valueFromBoolean(all);
				}
			};
		}
	}
	public static class ValueStream extends StreamType {
		static final StreamHandler HANDLER = new StreamHandler() {
				public void handlePush(Supplier<Boolean> finished,
						       Consumer<RödaValue> put,
						       RödaValue value) {
					put.accept(value);
				}
				public RödaValue handlePull(Supplier<Boolean> finished,
							    Supplier<RödaValue> get) {
					return get.get();
				}
				public RödaValue handleReadAll(Supplier<Boolean> finished,
							       Supplier<RödaValue> get) {
				        List<RödaValue> list = new ArrayList<RödaValue>();
					while (true) {
						RödaValue val = get.get();
						if (val == null) break;
						list.add(val);
					}
					return valueFromList(list);
				}
			};

		StreamHandler newHandler() {
			return HANDLER;
		}
	}
	// TODO mieti virrat uudestaan siten, että tämä toimii kunnolla
	// Ongelmat:
	// - Käytössä vain joko sisään- tai ulostulokäsittelijänä,
	//   vaikka loogisesti sen pitäisi olla molemmat
	// - Nykyinen virtajärjestelmä ei salli yhtäaikaisia
	//   sisään- ja ulostulokäsittelijöitä
	public static class SingleValueStream extends StreamType {
		StreamHandler newHandler() {
			return new StreamHandler() {
				boolean full = false;
				public void handlePush(Supplier<Boolean> finished,
						       Consumer<RödaValue> put,
						       RödaValue value) {
					if (full) {
						error("stream is full");
					}
					put.accept(value);
					full = true;
				}
				public RödaValue handlePull(Supplier<Boolean> finished,
							    Supplier<RödaValue> get) {
					return get.get();
				}
				public RödaValue handleReadAll(Supplier<Boolean> finished,
							       Supplier<RödaValue> get) {
				        List<RödaValue> list = new ArrayList<RödaValue>();
					while (true) {
						RödaValue val = get.get();
						if (val == null) break;
						list.add(val);
					}
					if (list.size() > 1) error("stream is full");
					if (list.size() < 1) error("stream is closed");
					return list.get(0);
				}
			};
		}
	}

	static class RödaStreamImpl extends RödaStream {
		BlockingQueue<RödaValue> queue = new LinkedBlockingQueue<>();
		boolean finished = false;
		
		@Override
		public RödaValue get() {
			//System.err.println("<PULL " + this + ">");
			while (queue.isEmpty() && !closed());
		
			if (closed()) return null;
			try {
				return queue.take();
			} catch (InterruptedException e) {
				error("threading error");
				return null;
			}
		}
		
		@Override
		public void put(RödaValue value) {
			//System.err.println("<PUSH " + value + " to " + this + ">");
			queue.add(value);
		}
		
		@Override
		public boolean finished() {
			return finished && queue.isEmpty();
		}
		
		@Override
		public void finish() {
			//System.err.println("<FINISH " + this + ">");
			finished = true;
		}
		
		@Override
		public String toString() {
			return ""+(char)('A'+id);
		}
		
		/* pitää kirjaa virroista debug-viestejä varten */
		private static int streamCounter = 0;
		
		int id; { id = streamCounter++; }
	}

	public static class ISLineStream extends RödaStream {
		private BufferedReader in;
		private boolean finished = false;
		public ISLineStream(BufferedReader in) {
			this.in = in;
		}
		{
			inHandler = ValueStream.HANDLER;
			outHandler = ValueStream.HANDLER;
		}
		public RödaValue get() {
			if (finished) return null;
			try {
				while (!in.ready()) {
					if (paused()) return null;
				}
				String line = in.readLine();
				if (line == null) return null;
				else return valueFromString(line + "\n");
			} catch (IOException e) {
				error(e);
				return null;
			}
		}
		public void put(RödaValue val) {
			error("no output to input");
		}
		public boolean finished() {
			try {
				return finished || !in.ready();
			} catch (IOException e) {
				return false; // Pitäisikö olla virheidenkäsittely?
			}
		}
		public void finish() {
			finished = true;
			try {
				in.close();
			} catch (IOException e) {
				error(e);
			}
		}
	}

	public static class OSStream extends RödaStream {
		private PrintWriter out;
		public OSStream(PrintWriter out) {
			this.out = out;
		}
		{
			inHandler = ValueStream.HANDLER;
			outHandler = ValueStream.HANDLER;
		}
		public RödaValue get() {
			error("no input from output");
			return null;
		}
		public void put(RödaValue val) {
			if (!closed()) {
				String str = val.str();
				out.print(str);
				if (str.indexOf('\n') != -1)
					out.flush();
			}
			else error("stream is closed");
		}
		public boolean finished() {
			return finished;
		}
		boolean finished = false;
		public void finish() {
			finished = true;
			out.flush();
			out.close();
		}
	};
}
