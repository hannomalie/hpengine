package main.renderer;

import java.util.List;
import java.util.concurrent.SynchronousQueue;

import main.World;
import main.camera.Camera;
import main.model.EntityFactory;
import main.model.IEntity;
import main.model.Model;
import main.model.OBJLoader;
import main.octree.Octree;
import main.renderer.command.Command;
import main.renderer.light.Spotlight;
import main.renderer.material.MaterialFactory;
import main.shader.Program;
import main.shader.ProgramFactory;
import main.texture.CubeMap;
import main.texture.TextureFactory;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Vector3f;

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
	public void draw(Camera camera, Octree octree, List<IEntity> entities, Spotlight light);
	public void update(World world, float seconds);
	public float getElapsedSeconds();
	public void drawDebug(Camera camera, Octree octree, List<IEntity> entities, Spotlight light);
	public Program getLastUsedProgram();
	public void setLastUsedProgram(Program firstPassProgram);
	public CubeMap getEnvironmentMap();
	public MaterialFactory getMaterialFactory();
	public TextureFactory getTextureFactory();
	public OBJLoader getOBJLoader();
	public EntityFactory getEntityFactory();
	public Model getSphere();
	void drawLine(Vector3f from, Vector3f to);
	public <T extends Result> SynchronousQueue<T> addCommand(Command<T> command);
	public ProgramFactory getProgramFactory();

}
