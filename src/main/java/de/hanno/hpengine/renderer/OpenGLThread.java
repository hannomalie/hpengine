package de.hanno.hpengine.renderer;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.SharedDrawable;

public class OpenGLThread extends Thread {

	private String name;
	SharedDrawable drawable;
	public volatile boolean initialized = false;

	private Runnable runnable;

	public OpenGLThread() {
		this(OpenGLContext.OPENGL_THREAD_NAME + "_" + System.currentTimeMillis());
	}
	public OpenGLThread(Runnable runnable) {
		this(OpenGLContext.OPENGL_THREAD_NAME + "_" + System.currentTimeMillis(), runnable);
	}
	public OpenGLThread(String name) {
		this(name, new Runnable() {
			@Override
			public void run() {

			}
		});
	}

	public OpenGLThread(String name, Runnable runnable) {
		this.name = name;
		drawable = OpenGLContext.getInstance().calculate(() -> new SharedDrawable(Display.getDrawable()));
		setRunnable(runnable);
		initialized = true;
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
		if(runnable == null) {
			System.err.println("Runnable is null...");
			return;
		}
		try {
//			if (!drawable.isCurrent()) {
				drawable.makeCurrent();
//			}
			Thread.currentThread().setName(name);
	        runnable.run();

			drawable.releaseContext();
			drawable.destroy();
		} catch (Exception e) {
			e.printStackTrace();
		}
		setRunnable(null);
	}

	public void setRunnable(Runnable runnable) {
		this.runnable = runnable;
	}
}
