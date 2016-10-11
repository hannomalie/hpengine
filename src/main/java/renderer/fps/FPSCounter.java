package renderer.fps;


import java.util.Stack;

public class FPSCounter {
	
	SizedStack<Long> stack = new SizedStack<>(10);

    public FPSCounter() {
        for(int i = 0; i < stack.maxSize; i++) {
            stack.push(0L);
        }
    }
	
	public void update() {
		stack.push(getNanoTime());
	}
	private static long getNanoTime() {
		return System.nanoTime();
	}
	
	public float getFPS() {
		float msPerFrame = getMsPerFrame();
		return 1000f / msPerFrame;
	}
	
	public float getMsPerFrame() {
        long diffInNanosForNFrames = stack.getLast() - stack.get(0);
        return (diffInNanosForNFrames / stack.getMaxSize()) / (1000f * 1000f);
	}

    public double getDeltaInS() {
        long diffInNanos = stack.get(1) - stack.get(0);
        return diffInNanos / (1000f * 1000f * 1000f);
    }

    public class SizedStack<T> extends Stack<T> {
        private int maxSize;

        public SizedStack(int size) {
            super();
            this.maxSize = size;
        }

        @Override
        public Object push(Object object) {
            while (this.size() >= maxSize) {
                this.remove(0);
            }
            return super.push((T) object);
        }

        public int getMaxSize() {
            return maxSize;
        }

        public T getLast() {
            return this.get(this.size()-1);
        }
    }
}
