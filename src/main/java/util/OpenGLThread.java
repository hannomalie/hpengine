package util;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.SharedDrawable;

public abstract class OpenGLThread extends Thread {

	private String name;
	SharedDrawable drawable;
	public volatile boolean initialized = false;

	public OpenGLThread() {
		this("GLThread_" + System.currentTimeMillis());
	}
	public OpenGLThread(String name) {
		this.name = name;
		try {
			drawable = new SharedDrawable(Display.getDrawable());
			initialized = true;
		} catch (LWJGLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void start() {
		super.start();
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
			Thread.currentThread().setName(name);
	        doRun();

			drawable.destroy();
		} catch (LWJGLException e) {
			e.printStackTrace();
		}
		
	}

	public abstract void doRun();
}
