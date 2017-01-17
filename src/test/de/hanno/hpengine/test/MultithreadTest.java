package de.hanno.hpengine.test;

import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.OpenGLThread;
import junit.framework.Assert;
import org.junit.Test;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.SharedDrawable;

public class MultithreadTest extends TestWithRenderer {
    static volatile boolean success1 = false;
    static volatile boolean success2 = false;
	
	@Test
	public void secondThreadUsesGLContext() throws InterruptedException, LWJGLException {
		Thread worker =	new Thread(new Runnable() {

			SharedDrawable drawable = OpenGLContext.getInstance().calculate(() -> new SharedDrawable(Display.getDrawable()));
			
			@Override
			public void run() {
				try {
					if (!drawable.isCurrent()) {
						drawable.makeCurrent();
					}
			        
			        int id = GL11.glGenTextures();
			        if(id != -1) {
			        	success1 = true;
			        }
			        
			        drawable.destroy();
				} catch (LWJGLException e) {
					success1 = false;
					e.printStackTrace();
				}
			}
		});
		
		worker.start();
		worker.join();
		Assert.assertTrue(success1);
		success1 = false;
	}
	
	@Test
	public void openGLHelperThreadTest() throws InterruptedException {
		Thread worker =	new OpenGLThread(() -> {
			int id = GL11.glGenTextures();
			if(id != -1) {
				success2 = true;
			}
		});
		
		worker.start();
		worker.join();
		Assert.assertTrue(success2);
	}
}
