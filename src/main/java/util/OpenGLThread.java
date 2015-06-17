package util;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.SharedDrawable;

public abstract class OpenGLThread extends Thread {

	SharedDrawable drawable;
	public volatile boolean initialized = false;
	
	public OpenGLThread() {
		try {
			drawable = new SharedDrawable(Display.getDrawable());
			initialized = true;
		} catch (LWJGLException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		if(!initialized) {
			System.err.println("Thread not initialized...");
			return;
		}
		try {
			if (!drawable.isCurrent()) {
				drawable.makeCurrent();
			}
	        doRun();

			drawable.destroy();
		} catch (LWJGLException e) {
			e.printStackTrace();
		}
		
	}

	public abstract void doRun();
}
