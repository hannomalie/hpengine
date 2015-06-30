package renderer;

import camera.Camera;
import com.bulletphysics.dynamics.DynamicsWorld;
import engine.World;
import engine.model.Entity;
import engine.model.EntityFactory;
import engine.model.Model;
import engine.model.OBJLoader;
import octree.Octree;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Vector3f;
import renderer.command.Command;
import renderer.light.LightFactory;
import renderer.material.MaterialFactory;
import scene.EnvironmentProbe;
import scene.EnvironmentProbeFactory;
import shader.AbstractProgram;
import shader.Program;
import shader.ProgramFactory;
import shader.StorageBuffer;
import texture.CubeMap;
import texture.TextureFactory;

import java.util.List;
import java.util.concurrent.SynchronousQueue;

 public interface Renderer {
	boolean CHECKERRORS = false;

	 static void exitOnGLError(String errorMessage) {
		if (!CHECKERRORS) {return;}
		
		int errorValue = GL11.glGetError();
		
		if (errorValue != GL11.GL_NO_ERROR) {
			String errorString = GLU.gluErrorString(errorValue);
			System.err.println("ERROR - " + errorMessage + ": " + errorString);
			
			if (Display.isCreated()) Display.destroy();
			System.exit(-1);
		}
	}
	 boolean isInitialized();
	void destroy();
	void draw(Entity camera, World world, List<Entity> entities);
	void update(World world, float seconds);
	float getElapsedSeconds();
	void drawDebug(Entity camera, DynamicsWorld dynamicsWorld, Octree octree, List<Entity> entities);
	Program getLastUsedProgram();
	void setLastUsedProgram(Program firstPassProgram);
	CubeMap getEnvironmentMap();
	MaterialFactory getMaterialFactory();
	TextureFactory getTextureFactory();
	OBJLoader getOBJLoader();
	EntityFactory getEntityFactory();
	Model getSphere();
	void drawLine(Vector3f from, Vector3f to);
	void drawLines(Program firstPassProgram);

	<OBJECT_TYPE, RESULT_TYPE extends Result<OBJECT_TYPE>> SynchronousQueue<RESULT_TYPE> addCommand(Command<RESULT_TYPE> command);

	ProgramFactory getProgramFactory();
	LightFactory getLightFactory();
	EnvironmentProbeFactory getEnvironmentProbeFactory();
	void init(Octree octree);
	int getMaxTextureUnits();
	void blur2DTexture(int sourceTextureId, int mipmap, int width, int height, int internalFormat, boolean upscaleToFullscreen, int blurTimes);
	void blur2DTextureBilateral(int sourceTextureId, int edgeTexture, int width, int height, int internalFormat, boolean upscaleToFullscreen, int blurTimes);
	void addRenderProbeCommand(EnvironmentProbe probe);
	void addRenderProbeCommand(EnvironmentProbe probe, boolean urgent);
	float getCurrentFPS();
	double getDeltaInS();
	int getFrameCount();
	AbstractProgram getFirstPassProgram();
	Program getSecondPassPointProgram();
	Program getSecondPassAreaLightProgram();
	Program getSecondPassTubeProgram();
	Program getSecondPassDirectionalProgram();
	Program getCombineProgram();
	Program getPostProcessProgram();
	StorageBuffer getStorageBuffer();
	 String getCurrentState();
}
