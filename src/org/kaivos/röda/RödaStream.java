package org.kaivos.röda;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;

import java.util.function.Consumer;
import java.util.function.Supplier;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.IOException;

import org.kaivos.röda.RödaValue;
import org.kaivos.röda.type.RödaList;
import org.kaivos.röda.type.RödaString;

import static org.kaivos.röda.Interpreter.error;

/**
 * RödaStream represents a pipe and can be used to transfer values from one
 * thread to another.
 *
 * Futhermore, RödaStream is a collection that holds all the current and future
 * values in a pipe. It can be used to iterate over all these values.
 */
public abstract class RödaStream implements Iterable<RödaValue> {
	private Deque<RödaValue> stack = new ArrayDeque<>();

	protected abstract RödaValue get();
	protected abstract void put(RödaValue value);

	/**
	 * Closes the stream permanently.
	 */
	public abstract void finish();

	/**
	 * Returns false if it is not possible to pull values from the stream.
	 * This is a non-blocking operation, and a truthy return value does not mean
	 * that it is possible to pull values from the stream.
	 */
	public boolean closed() {
		return finished() && stack.isEmpty();
	}
	
	/**
	 * Returns true if it is possible to pull values from the stream.
	 * This is a blocking operation.
	 */
	public boolean open() {
		return peek() != null;
	}

	/**
	 * Returns true if the stream is permanently finished.
	 */
	public abstract boolean finished();

	/**
	 * Pushes a new value to the stream.
	 */
	public final void push(RödaValue value) {
		put(value);
	}
	
	/**
	 * Adds a new value to the stack.
	 */
	public final void unpull(RödaValue value) {
		stack.addFirst(value);
	}

	/**
	 * Pulls a value from the stream, or, if the stack is not empty, from the stack.
	 *
	 * @return the value, or null if the stream is closed.
	 */
	public final RödaValue pull() {
		if (!stack.isEmpty()) return stack.removeFirst();
		return get();
	}
	
	/**
	 * Pulls a value from the stream and places it to the stack.
	 * Next time a value is pulled, it will be taken from the stack.
	 *
	 * @return the value, or null if the stream is closed.
	 */
	public final RödaValue peek() {
		RödaValue value = pull();
		if (value == null) return null;
		stack.addFirst(value);
		return value;
	}

	/**
	 * Returns a value that represents all current and future values in the
	 * stream.
	 */
	public final RödaValue readAll() {
		List<RödaValue> list = new ArrayList<>();
		while (true) {
			RödaValue val = pull();
			if (val == null)
				break;
			list.add(val);
		}
		return RödaList.of(list);
	}

	/**
	 * Calls the given consumer for all current and future values in the stream.
	 * 
	 * @param consumer
	 *            the callback function used to consume the values
	 */
	public final void forAll(Consumer<RödaValue> consumer) {
		while (true) {
			RödaValue val = pull();
			if (val == null)
				break;
			consumer.accept(val);
		}
	}

	/**
	 * Returns a iterator that iterates over all the current and future values
	 * in the stream.
	 */
	@Override
	public Iterator<RödaValue> iterator() {
		return new Iterator<RödaValue>() {
			RödaValue buffer;
			{
				buffer = pull();
			}

			@Override
			public boolean hasNext() {
				return buffer != null;
			}

			@Override
			public RödaValue next() {
				RödaValue tmp = buffer;
				buffer = pull();
				return tmp;
			}
		};
	}

	public static RödaStream makeStream() {
		RödaStream stream = new RödaStreamImpl();
		return stream;
	}

	public static RödaStream makeEmptyStream() {
		RödaStream stream = new RödaStreamImpl();
		stream.finish();
		return stream;
	}

	public static RödaStream makeStream(Consumer<RödaValue> put, Supplier<RödaValue> get, Runnable finish,
			Supplier<Boolean> finished) {
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
		return stream;
	}

	static class RödaStreamImpl extends RödaStream {
		BlockingQueue<Optional<RödaValue>> queue = new LinkedBlockingQueue<>();
		boolean finished = false;

		@Override
		public RödaValue get() {
			if (finished) return null;
			Optional<RödaValue> value;
			try {
				value = queue.take();
			} catch (InterruptedException e) {
				error(e);
				return null;
			}
			finished = !value.isPresent();
			return value.orElse(null);
		}

		@Override
		public void put(RödaValue value) {
			try {
				queue.put(Optional.of(value));
			} catch (InterruptedException e) {
				error(e);
			}
		}

		@Override
		public boolean finished() {
			return finished;
		}

		@Override
		public void finish() {
			queue.add(Optional.empty());
		}

		@Override
		public String toString() {
			return "" + (char) ('A' + id);
		}

		/* pitää kirjaa virroista debug-viestejä varten */
		private static int streamCounter = 0;

		int id;
		{
			id = streamCounter++;
		}
	}
	
	public static enum ISStreamMode {
		LINE,
		CHARACTER
	}

	public static class ISStream extends RödaStream {
		private BufferedReader in;
		private boolean finished = false;
		private ISStreamMode mode = ISStreamMode.LINE;

		public ISStream(BufferedReader in) {
			this.in = in;
		}
		
		public void setMode(ISStreamMode mode) {
			this.mode = mode;
		}

		public RödaValue get() {
			if (finished)
				return null;
			try {
				switch (mode) {
				case LINE:
					String line = in.readLine();
					if (line == null)
						return null;
					else
						return RödaString.of(line);
				case CHARACTER:
					int chr = in.read();
					if (chr == -1)
						return null;
					else
						return RödaString.of(Character.toString((char) chr));
				default:
					error("invalid input mode");
					return null;
				}
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

		public RödaValue get() {
			error("no input from output");
			return null;
		}

		public void put(RödaValue val) {
			if (!closed()) {
				String str = val.str();
				out.print(str);
				out.flush();
			} else
				error("stream is closed");
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
