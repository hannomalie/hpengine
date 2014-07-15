package main.renderer;

import org.lwjgl.Sys;

public class FPSCounter {
	
	private final long[] lastFrameTimes = new long[] {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
	
	
	public void update(float elapsedSeconds) {
		for(int i = 0; i < lastFrameTimes.length-1; i++) {
			lastFrameTimes[i] = lastFrameTimes[i+1];
		}
		lastFrameTimes[lastFrameTimes.length-1] = getTime();
	}
	private static long getTime() {
		return (Sys.getTime() * 1000) / Sys.getTimerResolution();
	}
	
	public float getFPS() {
		return (lastFrameTimes[lastFrameTimes.length-1] - lastFrameTimes[0]) / lastFrameTimes.length;
	}
	
}
