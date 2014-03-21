package main;

import static main.log.ConsoleLogger.getLogger;

import java.nio.FloatBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.Material.MAP;
import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;

public class ForwardRenderer implements Renderer {
	private static Logger LOGGER = getLogger();

	public static EnumSet<DataChannels> RENDERTOQUAD = EnumSet.of(
			DataChannels.POSITION3,
			DataChannels.TEXCOORD);
	
	public int testTexture = -1;
	public static final int WIDTH = 1280;
	public static final int HEIGHT = 720;

	public static final boolean FRUSTUMCULLING = false;

	private int fps;
	private long lastFPS;
	private long lastFrame;

	private static int modelMatrixLocation;
	private static int modelMatrixShadowLocation;
	private static Program materialProgram;
	private FloatBuffer matrix44Buffer = BufferUtils.createFloatBuffer(16);
	private int renderedTextureShaderLocation;
	private static Program renderToQuadProgram;
	private static Program shadowMapProgram;

	private static int eyePosition;
	private RenderTarget firstTarget;
	private RenderTarget secondTarget;
	private int projectionMatrixLocation;
	private int viewMatrixLocation;

	private VertexBuffer fullscreenBuffer;
	private VertexBuffer debugBuffer;
	
	public ForwardRenderer(Spotlight light) {
		setupOpenGL();
		setupShaders();
		getDelta();
		lastFPS = Util.getTime();
		Mouse.setCursorPosition(WIDTH/2, HEIGHT/2);
		secondTarget = light.getRenderTarget();
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

		firstTarget = new RenderTarget(WIDTH, HEIGHT);

		fullscreenBuffer = new QuadVertexBuffer( true).upload();
		debugBuffer = new QuadVertexBuffer( false).upload();
		
		
		ForwardRenderer.exitOnGLError("setupOpenGL");
	}
	


	private void setupShaders() {
		
		materialProgram = new Program("/assets/shaders/vertex.glsl", "/assets/shaders/fragment.glsl", Entity.DEFAULTCHANNELS);
		materialProgram.use();
		setUpMaterialProgramTextureLocations(materialProgram);
		
		projectionMatrixLocation = GL20.glGetUniformLocation(materialProgram.getId(),"projectionMatrix");
		viewMatrixLocation = GL20.glGetUniformLocation(materialProgram.getId(), "viewMatrix");
		ForwardRenderer.modelMatrixLocation = GL20.glGetUniformLocation(materialProgram.getId(), "modelMatrix");
		ForwardRenderer.eyePosition = GL20.glGetUniformLocation(materialProgram.getId(), "eyePosition");
		//World.lightPositionLocation = GL20.glGetUniformLocation(materialProgramId, "lightPosition");
		World.light.setLightDirectionLocation(materialProgram.getId());
		World.light.setLightMatrixLocation(materialProgram.getId());
		World.useParallaxLocation = GL20.glGetUniformLocation(materialProgram.getId(), "useParallax");

		shadowMapProgram = new Program("/assets/shaders/shadowmap_vertex.glsl", "/assets/shaders/shadowmap_fragment.glsl", Entity.SHADOWCHANNELS);
		shadowMapProgram.use();
		ForwardRenderer.setModelMatrixShadowLocation(GL20.glGetUniformLocation(shadowMapProgram.getId(), "modelMatrix"));
		
		renderToQuadProgram = new Program("/assets/shaders/passthrough_vertex.glsl", "/assets/shaders/simpletexture_fragment.glsl", RENDERTOQUAD);
		renderToQuadProgram.use();
		renderedTextureShaderLocation = GL20.glGetUniformLocation(renderToQuadProgram.getId(), "renderedTexture");

		
		ForwardRenderer.exitOnGLError("setupShaders");
	}

	private void setUpMaterialProgramTextureLocations(Program program) {
		
		for (MAP map : MAP.values()) {
			String name = map.shaderVariableName;
			LOGGER.log(Level.INFO, String.format("Shader location for %s is set to %d", name, map.textureSlot));
			GL20.glUniform1i(GL20.glGetUniformLocation(program.getId(), name), map.textureSlot);
		}
		
		LOGGER.log(Level.INFO, String.format("Shader location for %s is set to %d", "shadowMap", 5));
		GL20.glUniform1i(GL20.glGetUniformLocation(program.getId(), "shadowMap"), 5);

		LOGGER.log(Level.INFO, String.format("Shader location for %s is %d", "shadowMap", GL20.glGetUniformLocation(program.getId(), "shadowMap")));
	}
//	private void setUpMaterialProgramTextureLocations(Program program) {
//		
//		for(int i = 0; i < Material.mapNames.length; i++) {
//			String name = Material.mapNames[i];
//			LOGGER.log(Level.INFO, String.format("Shader location for %s is set to %d", name, i));
//			GL20.glUniform1i(GL20.glGetUniformLocation(program.getId(), name), i);
//
//			LOGGER.log(Level.INFO, String.format("Shader location for %s is %d", name, GL20.glGetUniformLocation(program.getId(), name)));
//		}
//		
//		LOGGER.log(Level.INFO, String.format("Shader location for %s is set to %d", "shadowMap", 5));
//		GL20.glUniform1i(GL20.glGetUniformLocation(program.getId(), "shadowMap"), 5);
//
//		LOGGER.log(Level.INFO, String.format("Shader location for %s is %d", "shadowMap", GL20.glGetUniformLocation(program.getId(), "shadowMap")));
//	}

	public int getDelta() {
		long time = Util.getTime();
		int delta = (int) (time - lastFrame);
		lastFrame = time;
		 
		return delta;
	}

	public static int getModelMatrixLocation() {
		return modelMatrixLocation;
	}

	public void setModelMatrixLocation(int modelMatrixLocation) {
		ForwardRenderer.modelMatrixLocation = modelMatrixLocation;
	}

	public static int getModelMatrixShadowLocation() {
		return modelMatrixShadowLocation;
	}

	public static void setModelMatrixShadowLocation(int modelMatrixShadowLocation) {
		ForwardRenderer.modelMatrixShadowLocation = modelMatrixShadowLocation;
	}

	public void update() {
		updateFPS();
		materialProgram.use();
	}
	
	public void draw(Camera camera, List<IEntity> entities, Spotlight light) {
		draw(firstTarget, camera, entities, light);
	}
	
	private void draw(RenderTarget target, Camera camera, List<IEntity> entities, Spotlight light) {

		drawShadowMap(light, entities);
		
		target.use(true);

		materialProgram.use();
		
		// USE SHADOWMAP AND SHADOW MATRICES
		light.getCamera().getProjectionMatrix().store(matrix44Buffer); matrix44Buffer.flip();
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(materialProgram.getId(),"projectionMatrixShadow"), false, matrix44Buffer);
		light.getCamera().getViewMatrix().store(matrix44Buffer); matrix44Buffer.flip();
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(materialProgram.getId(),"viewMatrixShadow"), false, matrix44Buffer);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 5);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, light.getRenderTarget().getRenderedTexture());
		///////////
		
		int notInfrustum = 0;
		for (IEntity entity: entities) {
			if (!FRUSTUMCULLING || entity.isInFrustum(camera)) {
				entity.draw();
			} else {
				entity.drawDebug();
				notInfrustum++;
			}
		}
		
		LOGGER.log(Level.WARNING, String.format("%d not in frustum", notInfrustum));
		
		light.drawDebug();
		
		target.unuse();

		if (testTexture < 0) {
			testTexture = loadTextureToGL("/assets/textures/wood_diffuse.png");
		}
		
//		light.getRenderTarget().saveBuffer("c:/buffer_" + Util.getTime() + ".png");
//		System.exit(0);
		GL11.glViewport(0, 0, WIDTH, HEIGHT);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

		GL11.glDisable(GL11.GL_DEPTH_TEST);
		drawToQuad(target.getRenderedTexture(), fullscreenBuffer);
//		drawToQuad(light.getRenderTarget().getRenderedTexture(), debugBuffer);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}
	
	public void drawShadowMap(Spotlight light, List<IEntity> entities) {
		
		light.getRenderTarget().use(true);
		
		shadowMapProgram.use();

		 for (IEntity entity: entities) {
			 if (true) {
				 entity.drawShadow();
			 }
		 }
		
		light.getRenderTarget().unuse();
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer) {
		renderToQuadProgram.use();

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
		materialProgram.delete();

		ForwardRenderer.exitOnGLError("destroyOpenGL");
		
		Display.destroy();
	}

	public int loadShader(String filename, int type) {
		String shaderSource;
		int shaderID = 0;
		
		shaderSource = Util.loadAsTextFile(filename);
		
		shaderID = GL20.glCreateShader(type);
		GL20.glShaderSource(shaderID, shaderSource);
		GL20.glCompileShader(shaderID);
		
		if (GL20.glGetShader(shaderID, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			System.err.println("Could not compile shader.");
			System.exit(-1);
		}
		
		ForwardRenderer.exitOnGLError("loadShader");
		
		return shaderID;
	}

	
	public static int loadTextureToGL(String filename) {

		
		TextureBuffer texture = Util.loadPNGTexture(filename);

		int texId = GL11.glGenTextures();
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + Material.textureIndex);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, texture.getWidth(), texture.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texture.getBuffer());
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
		
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);

		
		ForwardRenderer.exitOnGLError("loadPNGTexture");
		Material.textureIndex++;

		return texId;
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

	public static int getMaterialProgramId() {
		return ForwardRenderer.materialProgram.getId();
	}

	public static void setMaterialProgramId(int materialProgramId) {
		ForwardRenderer.setMaterialProgramId(materialProgramId);
	}

	public static int getShadowProgramId() {
		return shadowMapProgram.getId();
	}

	public RenderTarget getFirstTarget() {
		return firstTarget;
	}

	public int getProjectionMatrixLocation() {
		return projectionMatrixLocation;
	}
	public int getViewMatrixLocation() {
		return viewMatrixLocation;
	}

	public static Program getMaterialProgram() {
		return materialProgram;
	}

	public int getEyePositionLocation() {
		return eyePosition;
	}

	public static Program getShadowProgram() {
		return shadowMapProgram;
	}
}
