package main;

import static main.log.ConsoleLogger.getLogger;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.util.OBJLoader;
import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Vector3f;

public class DeferredRenderer implements Renderer {
	private static Logger LOGGER = getLogger();

	public static EnumSet<DataChannels> RENDERTOQUAD = EnumSet.of(
			DataChannels.POSITION3,
			DataChannels.TEXCOORD);
	
	public int testTexture = -1;
	public static final int WIDTH = 1280;
	public static final int HEIGHT = 720;

	private int fps;
	private long lastFPS;
	private long lastFrame;

	private FloatBuffer matrix44Buffer = BufferUtils.createFloatBuffer(16);
	private Program firstPassProgram;
	private Program secondPassPointProgram;
	private Program secondPassDirectionalProgram;
	private Program combineProgram;
	private static Program renderToQuadProgram;

	private RenderTarget finalTarget;
	private RenderTarget firstPassTarget;
	private RenderTarget secondPassTarget;

	private VertexBuffer fullscreenBuffer;
	private VertexBuffer debugBuffer;

	private static int MAXLIGHTS = 64;
	private static int MAXLIGHTHALF = MAXLIGHTS/2;
	private List<Vector3f> pointLights = new ArrayList<>();
	
	private Entity sphere;
	
	public DeferredRenderer(Spotlight light) {
		setupOpenGL();
		setupShaders();
		getDelta();
		lastFPS = Util.getTime();
		Mouse.setCursorPosition(WIDTH/2, HEIGHT/2);
		for (int i = 0; i < MAXLIGHTHALF; i++) {
			Vector3f position = new Vector3f();
			position.x += (i) / 3f;
			position.z += (i);
			position.y = 3;
			pointLights.add(position);
		}
		for (int i = 0; i < MAXLIGHTHALF; i++) {
			Vector3f position = new Vector3f();
			position.x += (i) / 3;
			position.z += (i);
			pointLights.add(position);
		}
		Model sphereModel;
		try {
			sphereModel = OBJLoader.loadTexturedModel(new File("C:\\sphere.obj")).get(0);
			sphere = new Entity(this, sphereModel);
			sphere.setScale(new Vector3f(50,50,50));
			sphere.update();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void setupOpenGL() {
		try {
			PixelFormat pixelFormat = new PixelFormat();
			ContextAttribs contextAtrributes = new ContextAttribs(4, 2);
//				.withForwardCompatible(true);
//				.withProfileCore(true);
			
			Display.setDisplayMode(new DisplayMode(WIDTH, HEIGHT));
			Display.setTitle("ForwardRenderer");
			Display.create(pixelFormat, contextAtrributes);
			
			GL11.glViewport(0, 0, WIDTH, HEIGHT);
		} catch (LWJGLException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		
		// Setup an XNA like background color
		GL11.glClearColor(0.4f, 0.6f, 0.9f, 0f);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_CULL_FACE);
//		GL11.glDisable(GL11.GL_CULL_FACE);
		
		// Map the internal OpenGL coordinate system to the entire screen
		GL11.glViewport(0, 0, WIDTH, HEIGHT);

		finalTarget = new RenderTarget(WIDTH, HEIGHT, GL11.GL_RGB, 1);
		firstPassTarget = new RenderTarget(WIDTH, HEIGHT, GL30.GL_RGB32F, 3);
		secondPassTarget = new RenderTarget(WIDTH, HEIGHT, GL30.GL_RGB32F, 1);

		fullscreenBuffer = new QuadVertexBuffer( true).upload();
		debugBuffer = new QuadVertexBuffer( false).upload();
		
		DeferredRenderer.exitOnGLError("setupOpenGL");
	}
	


	private void setupShaders() {
		
		firstPassProgram = new Program("/assets/shaders/deferred/first_pass_vertex.glsl", "/assets/shaders/deferred/first_pass_fragment.glsl", Entity.DEFAULTCHANNELS, true);
		
		secondPassPointProgram = new Program("/assets/shaders/deferred/second_pass_point_vertex.glsl", "/assets/shaders/deferred/second_pass_point_fragment.glsl", Entity.POSITIONCHANNEL, false);
		secondPassDirectionalProgram = new Program("/assets/shaders/deferred/second_pass_directional_vertex.glsl", "/assets/shaders/deferred/second_pass_directional_fragment.glsl", Entity.POSITIONCHANNEL, false);

		combineProgram = new Program("/assets/shaders/deferred/combine_pass_vertex.glsl", "/assets/shaders/deferred/combine_pass_fragment.glsl", RENDERTOQUAD, false);
		renderToQuadProgram = new Program("/assets/shaders/passthrough_vertex.glsl", "/assets/shaders/simpletexture_fragment.glsl", RENDERTOQUAD);

		DeferredRenderer.exitOnGLError("setupShaders");
	}

	public int getDelta() {
		long time = Util.getTime();
		int delta = (int) (time - lastFrame);
		lastFrame = time;
		 
		return delta;
	}

	public void update() {
		updateFPS();
	}
	
	public void draw(Camera camera, List<IEntity> entities, Spotlight light) {
		draw(finalTarget, camera, entities, light);
	}
	
	private void draw(RenderTarget target, Camera camera, List<IEntity> entities, Spotlight light) {

		drawFirstPass(camera, entities);
		drawSecondPass(camera, light, pointLights);
		combinePass(finalTarget, firstPassTarget, secondPassTarget);
		
		GL11.glViewport(0, 0, WIDTH, HEIGHT);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

		GL11.glDisable(GL11.GL_DEPTH_TEST);
		drawToQuad(finalTarget.getRenderedTexture(), fullscreenBuffer);
		if (World.DEBUGFRAME_ENABLED) {
			drawToQuad(secondPassTarget.getRenderedTexture(0), debugBuffer);
		}
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}

	private void drawFirstPass(Camera camera, List<IEntity> entities) {
		firstPassProgram.use();
		GL11.glDepthMask(true);
		firstPassTarget.use(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(firstPassProgram.getId(),"viewMatrix"), false, camera.getViewMatrixAsBuffer());
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(firstPassProgram.getId(),"projectionMatrix"), false, camera.getProjectionMatrixAsBuffer());
		GL20.glUniform3f(GL20.glGetUniformLocation(firstPassProgram.getId(),"eyePosition"), camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
		for (IEntity entity : entities) {
			entity.draw(firstPassProgram);
		}
//		firstPassTarget.unuse();
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
	}

	private void drawSecondPass(Camera camera, Spotlight directionalLight, List<Vector3f> pointLights) {

		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);

		secondPassTarget.use(false);
//		GL11.glClearColor (0.2f, 0.2f, 0.2f, 0.0f);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

		secondPassDirectionalProgram.use();
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(0));
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(1));
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(2));

		GL20.glUniformMatrix4(GL20.glGetUniformLocation(secondPassDirectionalProgram.getId(),"viewMatrix"), false, camera.getViewMatrixAsBuffer());
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(secondPassDirectionalProgram.getId(),"projectionMatrix"), false, camera.getProjectionMatrixAsBuffer());
		GL20.glUniform3f(GL20.glGetUniformLocation(secondPassDirectionalProgram.getId(),"lightDirection"), directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z );
		LOGGER.log(Level.INFO, String.format("DIR LIGHT: %f %f %f", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z));
		fullscreenBuffer.draw();

		secondPassPointProgram.use();
		
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(secondPassPointProgram.getId(),"viewMatrix"), false, camera.getViewMatrixAsBuffer());
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(secondPassPointProgram.getId(),"projectionMatrix"), false, camera.getProjectionMatrixAsBuffer());
		
		for (Vector3f lightPosition : pointLights) {

			GL20.glUniform3f(GL20.glGetUniformLocation(secondPassPointProgram.getId(),"lightPosition"), lightPosition.x, lightPosition.y, lightPosition.z );
			GL20.glUniform3f(GL20.glGetUniformLocation(secondPassPointProgram.getId(),"lightDiffuse"), 1,1,1 );
			GL20.glUniform3f(GL20.glGetUniformLocation(secondPassPointProgram.getId(),"lightSpecular"), 1,1,1 );
			sphere.draw(secondPassPointProgram);
		}
		secondPassTarget.unuse();
		GL11.glDisable(GL11.GL_BLEND);
	}


	private void combinePass(RenderTarget target, RenderTarget gBuffer, RenderTarget laBuffer) {
		combineProgram.use();
		target.use(true);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(2));
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, secondPassTarget.getRenderedTexture(0));
		
		fullscreenBuffer.draw();

		target.unuse();
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer) {
		drawToQuad(texture, buffer, renderToQuadProgram);
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer, Program program) {
		program.use();

		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

		buffer.draw();
		
		exitOnGLError("glDrawArrays in drawToQuad");
	}

	public void destroy() {
		destroyOpenGL();
	}

	private void destroyOpenGL() {
		// Delete the shaders
//		materialProgram.delete();
//
//		DeferredRenderer.exitOnGLError("destroyOpenGL");
		
		Display.destroy();
	}

	public void updateFPS() {
		if (Util.getTime() - lastFPS > 1000) {
			Display.setTitle("FPS: " + fps);
			fps = 0;
			lastFPS += 1000;
		}
		fps++;
	}

	public static void exitOnGLError(String errorMessage) {
		int errorValue = GL11.glGetError();
		
		if (errorValue != GL11.GL_NO_ERROR) {
			String errorString = GLU.gluErrorString(errorValue);
			System.err.println("ERROR - " + errorMessage + ": " + errorString);
			
			if (Display.isCreated()) Display.destroy();
			System.exit(-1);
		}
	}
}
