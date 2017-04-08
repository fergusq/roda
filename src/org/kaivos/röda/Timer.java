package org.kaivos.röda;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * A simple timer that counts time in nanoseconds.
 * 
 * @author Iikka Hauhio
 *
 */
public class Timer {
	
	private long sum = 0, start = 0;
	
	private static final ThreadMXBean TMXB = ManagementFactory.getThreadMXBean();
	
	/**
	 * Starts the timer.
	 */
	public void start() {
		if (start != 0) throw new RuntimeException("Can't start a running timer.");
		
		start = TMXB.getCurrentThreadCpuTime();
	}
	
	/**
	 * Stops the timer and increments the value of the time with the number of
	 * nanoseconds since it was started.
	 */
	public void stop() {
		if (start == 0) throw new RuntimeException("Can't stop a stopped timer.");
		long diff = TMXB.getCurrentThreadCpuTime() - start;
		sum += diff;
		start = 0;
	}
	
	/**
	 * Resets the value of the timer.
	 */
	public void reset() {
		if (start != 0) throw new RuntimeException("Can't reset a running timer.");
		sum = 0;
	}
	
	/**
	 * Adds the value of the given timer to the value of this timer.
	 */
	public void add(Timer timer) {
		sum += timer.sum;
	}
	
	/**
	 * Returns the value of the timer, in milliseconds. The value is the total
	 * time the timer has been running.
	 * @return value of timer in milliseconds
	 */
	public long timeMillis() {
		return sum / 1_000_000;
	}
	
	/**
	 * Returns the value of the timer, in nanoseconds. The value is the total
	 * time the timer has been running.
	 * @return value of timer in nanoseconds
	 */
	public long timeNanos() {
		return sum;
	}
	
	@Override
	public String toString() {
		return "Timer@" + hashCode() + "{ sum = " + sum + " }";
	}

}
