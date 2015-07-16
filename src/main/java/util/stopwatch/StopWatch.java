package util.stopwatch;

import java.util.LinkedList;

import junit.framework.Assert;

import org.junit.Test;

public class StopWatch {
	public static boolean ACTIVE = false;
	public static boolean PRINT = false;
	
	public static StopWatch stopWatch;
	static {
		stopWatch = new StopWatch();
	}
	public static StopWatch getInstance() {
		return stopWatch;
	}
	private LinkedList<Watch> watches = new LinkedList<>();


	private StopWatch() {}
	public void start(String description) {
		if (!ACTIVE) {return;}
		watches.addLast(new Watch( System.nanoTime(), description));
	}

	public long stop() {
		if (!ACTIVE) {return 0;}
		return (System.currentTimeMillis() - watches.pollLast().getStart());
	}

	public String stopAndGetStringMS() {
		if (!ACTIVE) {return "";}
		Watch watch = watches.pollLast();
		return String.format("%s took %.3f ms", watch.getDescripton(), (System.nanoTime() - watch.getStart())/1000000f);
	}
	
	public void stopAndPrintMS() {
		if (!ACTIVE) {return;}
		if (!PRINT) {return;}
		String out = stopAndGetStringMS();
		System.out.println(getIntendation() + out);
	}
	
	private String getIntendation() {
		int n = watches.size();
		StringBuilder result = new StringBuilder();
		
		while(n > 0) {
			result.append("..");
			n--;
		}
		
		return result.toString();
	}
	
	@Test
	public void validation() {
		StopWatch.ACTIVE = true;
		
		StopWatch stopWatch = new StopWatch();

		stopWatch.start("abc");
		Assert.assertEquals(1, stopWatch.watches.size());
		stopWatch.start("xyz");
		Assert.assertEquals(2, stopWatch.watches.size());
		stopWatch.stop();
		Assert.assertEquals(1, stopWatch.watches.size());
		stopWatch.start("123");
		Assert.assertEquals(2, stopWatch.watches.size());
		stopWatch.stop();
		Assert.assertEquals(1, stopWatch.watches.size());
		stopWatch.stop();
		Assert.assertEquals(0, stopWatch.watches.size());
		
		StopWatch.ACTIVE = false;
	}

}
