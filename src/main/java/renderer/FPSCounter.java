package renderer;


public class FPSCounter {
	
	private final long[] lastFrameTimes = new long[] {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
	
	
	public void update() {
		for(int i = 0; i < lastFrameTimes.length-1; i++) {
			lastFrameTimes[i] = lastFrameTimes[i+1];
		}
		lastFrameTimes[lastFrameTimes.length-1] = getTime();
	}
	private static long getTime() {
		return System.currentTimeMillis();
	}
	
	public float getFPS() {
		float msPerFrame = getMsPerFrame();
		return 1000f / msPerFrame;
	}
	
	public float getMsPerFrame() {
		return (lastFrameTimes[lastFrameTimes.length-1] - lastFrameTimes[0]) / lastFrameTimes.length;
	}
	
}
