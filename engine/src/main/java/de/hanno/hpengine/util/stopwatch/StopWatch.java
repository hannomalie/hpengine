package de.hanno.hpengine.util.stopwatch;

import java.util.LinkedList;
import java.util.logging.Logger;

public class StopWatch {

    private static final Logger LOGGER = Logger.getLogger(StopWatch.class.getName());

	public static boolean ACTIVE = false;
	public static boolean PRINT = false;
	
	public static StopWatch stopWatch;
	static {
		stopWatch = new StopWatch();
	}
	public static StopWatch getInstance() {
		return stopWatch;
	}
	LinkedList<Watch> watches = new LinkedList<>();


	StopWatch() {}
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
		LOGGER.info(getIntendation() + out);
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
}
