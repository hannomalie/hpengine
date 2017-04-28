package de.hanno.hpengine.renderer.fps;


public class FPSCounter {

    final long[] stack = { 0,0,0,0,0,0,0,0,0,0 };
    int currentIndex = 0;

    public FPSCounter() {
    }
	
	public void update() {
		push(getNanoTime());
	}
	private static long getNanoTime() {
		return System.nanoTime();
	}
	
	public float getFPS() {
		float msPerFrame = getMsPerFrame();
		return 1000f / msPerFrame;
	}
	
	public float getMsPerFrame() {
        long diffInNanosForNFrames = getLast() - getCurrent();
        return (diffInNanosForNFrames / stack.length) / (1000f * 1000f);
	}

    public double getDeltaInS() {
        long diffInNanos = getLast() - getCurrent();
        return diffInNanos / (1000f * 1000f * 1000f);
    }

    public void push(long value) {
        stack[currentIndex] = value;
        currentIndex = currentIndex+1 > stack.length -1 ? 0 : currentIndex+1;
    }

    public long getLast() {
        return stack[currentIndex-1 < 0 ? stack.length-1 : currentIndex-1];
    }
    public long getCurrent() {
        return stack[currentIndex];
    }


}
