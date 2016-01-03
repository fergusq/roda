package org.kaivos.röda;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import java.util.function.Consumer;
import java.util.function.Supplier;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
	
	public abstract RödaValue get();
	public abstract void put(RödaValue value);
	public abstract void finish();
	public abstract boolean finished();
	
	final void push(RödaValue value) {
		inHandler.handlePush(this::finished, this::put, value);
	}
	
	final RödaValue pull() {
		return outHandler.handlePull(this::finished, this::get);
	}
	
	final RödaValue readAll() {
		return outHandler.handleReadAll(this::finished, this::get);
	}

	/**
	 * Returns a iterator that iterates over all the current and future values in the pipe.
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

	static abstract class StreamType {
		abstract StreamHandler newHandler();
	}
	private interface StreamHandler {
		void handlePush(Supplier<Boolean> finished, Consumer<RödaValue> put, RödaValue value);
		RödaValue handlePull(Supplier<Boolean> finished, Supplier<RödaValue> get);
		RödaValue handleReadAll(Supplier<Boolean> finished, Supplier<RödaValue> get);
	}
	static class LineStream extends StreamType {
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
	static class VoidStream extends StreamType {
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
	}
	static class ByteStream extends StreamType {
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
	static class BooleanStream extends StreamType {
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
	static class ValueStream extends StreamType {
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

	static class RödaStreamImpl extends RödaStream {
		BlockingQueue<RödaValue> queue = new LinkedBlockingQueue<>();
		boolean finished = false;
		
		@Override
		public RödaValue get() {
			//System.err.println("<PULL " + this + ">");
			while (queue.isEmpty() && !finished);
		
			if (finished()) return null;
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
			finished = true;
		}
		
		@Override
		public String toString() {
			return ""+(char)('A'+id);
		}
		
		/* pitää kirjaa virroista debug-viestejä varten */
		private static int streamCounter;
		
		int id; { id = streamCounter++; }
	}	
}
