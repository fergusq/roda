package org.kaivos.röda;

import java.lang.Iterable;
import java.util.Iterator;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

public final class IOUtils {
	private IOUtils() {}

	public static File getMaybeRelativeFile(File pwd, String name) {
		if (name.startsWith("/")) { // tee tästä yhteensopiva outojen käyttöjärjestelmien kanssa
			return new File(name);
		} else if (name.startsWith("~")) {
			return new File(System.getenv("HOME"), name.replaceAll("~/?", ""));
		}else {
			return new File(pwd, name);
		}
	}

	public static final Iterable<String> fileIterator(File file) {
		try {
			BufferedReader in = new BufferedReader(new FileReader(file));
			return lineIterator(in);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public static final Iterable<String> fileIterator(String file) {
		try {
			BufferedReader in = new BufferedReader(new FileReader(file));
			return lineIterator(in);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public static final Iterable<String> streamLineIterator(InputStream stream) {
		BufferedReader in = new BufferedReader(new InputStreamReader(stream));
		return lineIterator(in);
	}

	public static final Iterable<Character> streamCharacterIterator(InputStream stream) {
		BufferedReader in = new BufferedReader(new InputStreamReader(stream));
		return characterIterator(in);
	}

	public static final Iterable<String> lineIterator(BufferedReader r) {
		return () -> new Iterator<String>() {
				String buffer;
				{
					updateBuffer();
				}
				
				private void updateBuffer() {
					try {
						buffer = r.readLine();
						if (buffer == null) r.close();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}

				@Override public boolean hasNext() {
					return buffer != null;
				}
				
				@Override public String next() {
					String tmp = buffer;
					updateBuffer();
					return tmp;
				}
		};
	}

	public static final Iterable<Character> characterIterator(BufferedReader r) {
		return () -> new Iterator<Character>() {
				int buffer;
				{
					updateBuffer();
				}
				
				private void updateBuffer() {
					try {
						buffer = r.read();
						if (buffer == -1) r.close();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}

				@Override public boolean hasNext() {
					return buffer != -1;
				}
				
				@Override public Character next() {
					char tmp = (char) buffer;
					updateBuffer();
					return tmp;
				}
		};
	}
}
