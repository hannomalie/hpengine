package test;

import junit.framework.Assert;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.DirectionalLight;
import main.util.OpenGLThread;

import org.junit.BeforeClass;
import org.junit.Test;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.SharedDrawable;

public class MultithreadTest {
	static Renderer renderer;
	static volatile boolean success1 = false;
	
	@BeforeClass
	public static void init() {
		renderer = new DeferredRenderer(new DirectionalLight(true));
	}
	
	@Test
	public void secondThreadUsesGLContext() throws InterruptedException, LWJGLException {
		Thread worker =	new Thread(new Runnable() {

			SharedDrawable drawable = new SharedDrawable(Display.getDrawable());
			
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
		Thread worker =	new OpenGLThread() {
			
			@Override
			public void doRun() {
		        int id = GL11.glGenTextures();
		        if(id != -1) {
		        	success1 = true;
		        }
			}
		};
		
		worker.start();
		worker.join();
		Assert.assertTrue(success1);
    	success1 = false;
	}
}
