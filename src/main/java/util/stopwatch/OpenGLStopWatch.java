package util.stopwatch;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

public class OpenGLStopWatch {

	private int queryIdStart;
	private int queryIdEnd;
	private String description = "";
	
	public void start(String description) {
		this.description = description;
		queryIdStart = GL15.glGenQueries();
		queryIdEnd = GL15.glGenQueries();
		GL33.glQueryCounter(queryIdStart, GL33.GL_TIMESTAMP);
	}
	
	public void stop() {
		GL33.glQueryCounter(queryIdEnd, GL33.GL_TIMESTAMP);
	}
	
	public long getTimeInMS() {
		long stopTimerAvailable = 0;
		long start;
		long end;
		while(stopTimerAvailable == 0) {
			stopTimerAvailable = GL33.glGetQueryObjecti64(queryIdEnd, GL15.GL_QUERY_RESULT_AVAILABLE);
		}

		start = GL33.glGetQueryObjecti64(queryIdStart, GL15.GL_QUERY_RESULT);
		end = GL33.glGetQueryObjecti64(queryIdEnd, GL15.GL_QUERY_RESULT);
		
		return (end - start) / 1000000l;
	}
	
	public void printTimeInMS() {
		System.out.println(description + " took " + getTimeInMS() + " ms");
	}
	
	public long stopAndGetTimeInMS() {
		stop();
		return getTimeInMS();
	}
	
	public void stopAndPrintTimeInMS() {
		stop();
		printTimeInMS();
	}
}
