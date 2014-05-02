package main;

import java.util.List;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public interface Renderer {
	static final boolean CHECKERRORS = false;

	public static final int WIDTH = 1280;
	public static final int HEIGHT = 720;
	
	public static void exitOnGLError(String errorMessage) { 
		if (!CHECKERRORS) {return;}
		
		int errorValue = GL11.glGetError();
		
		if (errorValue != GL11.GL_NO_ERROR) {
			String errorString = GLU.gluErrorString(errorValue);
			System.err.println("ERROR - " + errorMessage + ": " + errorString);
			
			if (Display.isCreated()) Display.destroy();
			System.exit(-1);
		}
	}
	public void destroy();
	public void draw(Camera camera, List<IEntity> entities, Spotlight light);
	public void update();

}
