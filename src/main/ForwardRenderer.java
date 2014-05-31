package main;

import static main.log.ConsoleLogger.getLogger;

import java.nio.FloatBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.Material.MAP;
import main.shader.Program;
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
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
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

	private int fps;
	private long lastFPS;
	private long lastFrame;

	private FloatBuffer matrix44Buffer = BufferUtils.createFloatBuffer(16);
	private static Program materialProgram;
	private static Program renderToQuadProgram;
	private static Program shadowMapProgram;
	private static Program blurProgram;
	private static Program depthMapProgram;

	private RenderTarget firstTarget;
	private RenderTarget depthTarget;
	private RenderTarget depthBlurTarget;
	private RenderTarget shadowMapTarget;

	private VertexBuffer fullscreenBuffer;
	private VertexBuffer debugBuffer;

	
	public ForwardRenderer(Spotlight light) {
		setupOpenGL();
		setupShaders();
		getDelta();
		lastFPS = Util.getTime();
		Mouse.setCursorPosition(WIDTH/2, HEIGHT/2);
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

		firstTarget = new RenderTarget(WIDTH, HEIGHT, 1);
		depthTarget = new RenderTarget(1024, 1024, GL30.GL_RGB32F, 1, 1f, 1f, 1f, GL11.GL_LINEAR, 1);
		depthBlurTarget = new RenderTarget(1024, 1024, GL30.GL_RGB32F, 1, 1f, 1f, 1f, GL11.GL_LINEAR, 1);
		shadowMapTarget = new RenderTarget(1024, 1024, GL30.GL_RGB32F, 1, 1f, 1f, 1f, GL11.GL_LINEAR, 1);

		fullscreenBuffer = new QuadVertexBuffer( true).upload();
		debugBuffer = new QuadVertexBuffer( false).upload();
		
		
		Renderer.exitOnGLError("setupOpenGL");
	}
	


	private void setupShaders() {
		
		materialProgram = new Program("/assets/shaders/vertex.glsl", "/assets/shaders/fragment.glsl", Entity.DEFAULTCHANNELS);
//		materialProgram.use();
//		setUpMaterialProgramTextureLocations(materialProgram);
		
		World.useParallaxLocation = GL20.glGetUniformLocation(materialProgram.getId(), "useParallax");
		World.useSteepParallaxLocation = GL20.glGetUniformLocation(materialProgram.getId(), "useSteepParallax");

		shadowMapProgram = new Program("/assets/shaders/mvp_vertex.glsl", "/assets/shaders/shadowmap_fragment.glsl", Entity.SHADOWCHANNELS);
		depthMapProgram = new Program("/assets/shaders/mvp_vertex.glsl", "/assets/shaders/depthmap_fragment.glsl", Entity.DEPTHCHANNELS);
		
		renderToQuadProgram = new Program("/assets/shaders/passthrough_vertex.glsl", "/assets/shaders/simpletexture_fragment.glsl", RENDERTOQUAD);

		blurProgram = new Program("/assets/shaders/passthrough_vertex.glsl", "/assets/shaders/blur_fragment.glsl", RENDERTOQUAD);

		
		Renderer.exitOnGLError("setupShaders");
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
		
		LOGGER.log(Level.INFO, String.format("Shader location for %s is set to %d", "depthMap", 6));
		GL20.glUniform1i(GL20.glGetUniformLocation(program.getId(), "depthMap"), 6);

		LOGGER.log(Level.INFO, String.format("Shader location for %s is %d", "depthMap", GL20.glGetUniformLocation(program.getId(), "depthMap")));
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
		draw(firstTarget, camera, entities, light);
	}
	
	private void draw(RenderTarget target, Camera camera, List<IEntity> entities, Spotlight light) {

		drawShadowMap(light, entities, shadowMapTarget);
		if (World.useAmbientOcclusion == 1) {
			drawDepthMap(camera, entities, depthTarget);
		}
		target.use(true);

		materialProgram.use();

		GL20.glUniform1i(GL20.glGetUniformLocation(materialProgram.getId(),"frustumCulling"), World.useFrustumCulling);
		GL20.glUniform4(GL20.glGetUniformLocation(materialProgram.getId(),"FrustumPlanes"), camera.getFrustum().toFloatBuffer());

		// USE LIGHT AND CAMERA
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(materialProgram.getId(),"viewMatrix"), false, camera.getViewMatrixAsBuffer());
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(materialProgram.getId(),"projectionMatrix"), false, camera.getProjectionMatrixAsBuffer());
		GL20.glUniform3f(GL20.glGetUniformLocation(materialProgram.getId(),"eyePosition"), camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
		GL20.glUniform3f(GL20.glGetUniformLocation(materialProgram.getId(),"lightPosition"), light.getPosition().x, light.getPosition().y, light.getPosition().z );
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(materialProgram.getId(),"lightMatrix"), false, light.getLightMatrix());
		GL20.glUniform1i(GL20.glGetUniformLocation(materialProgram.getId(),"useParallax"), World.useParallax);
		GL20.glUniform1i(GL20.glGetUniformLocation(materialProgram.getId(),"useSteepParallax"), World.useSteepParallax);
		GL20.glUniform1i(GL20.glGetUniformLocation(materialProgram.getId(),"useAmbientOcclusion"), World.useAmbientOcclusion);
		
		// USE SHADOWMAP AND SHADOW MATRICES, DEPTHMAP
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(materialProgram.getId(),"projectionMatrixShadow"), false, light.getCamera().getProjectionMatrixAsBuffer());
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(materialProgram.getId(),"viewMatrixShadow"), false, light.getCamera().getViewMatrixAsBuffer());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 5);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowMapTarget.getRenderedTexture());
		if (World.useAmbientOcclusion == 1) {
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthBlurTarget.getRenderedTexture());
		}
		
		int notInfrustum = 0;
		materialProgram.use();
		for (IEntity entity: entities) {
			if (World.useFrustumCulling == 1) {
				if(entity.isInFrustum(camera)) {
					entity.draw(materialProgram, null);
				} else {
//					entity.drawDebug();
				}
			} else {
				entity.draw(materialProgram, null);
				notInfrustum++;
			}
		}
		
//		LOGGER.log(Level.WARNING, String.format("%d not in frustum", notInfrustum));
		
//		light.drawDebug();
		
		target.unuse();

//		depthTarget.saveBuffer("c:/buffer_" + Util.getTime() + ".png");
//		System.exit(0);
		
		GL11.glViewport(0, 0, WIDTH, HEIGHT);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

		GL11.glDisable(GL11.GL_DEPTH_TEST);
		drawToQuad(target.getRenderedTexture(), fullscreenBuffer);
		if (World.DEBUGFRAME_ENABLED) {
			drawToQuad(shadowMapTarget.getRenderedTexture(), debugBuffer);
		}
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}

	private void drawDepthMap(Camera camera, List<IEntity> entities, RenderTarget target) {
		target.use(true);
		
		depthMapProgram.use();
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(shadowMapProgram.getId(),"viewMatrix"), false, camera.getViewMatrixAsBuffer());
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(shadowMapProgram.getId(),"projectionMatrix"), false, camera.getProjectionMatrixAsBuffer());

		for (IEntity entity: entities) {
			entity.draw(depthMapProgram, null);
		}
		
		target.unuse();

		blur(target.getRenderedTexture(), depthBlurTarget);
	}

	public void drawShadowMap(Spotlight light, List<IEntity> entities, RenderTarget target) {
		
		light.getRenderTarget().use(true);
		//target.use(true);

		shadowMapProgram.use();
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(shadowMapProgram.getId(),"viewMatrix"), false, light.getCamera().getViewMatrixAsBuffer());
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(shadowMapProgram.getId(),"projectionMatrix"), false, light.getCamera().getProjectionMatrixAsBuffer());

		 for (IEntity entity: entities) {
			 if (true) {
				 entity.draw(shadowMapProgram, null);
			 }
		 }
		
		light.getRenderTarget().unuse();
		//target.unuse();

		blur(light.getRenderTarget().getRenderedTexture(), target);
	}
	
	private void blur(int texture, RenderTarget target) {
		target.use(true);
		drawToQuad(texture, fullscreenBuffer, blurProgram);
		target.unuse();
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer, Program program) {
		program.use();

		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

		buffer.draw();
		
		Renderer.exitOnGLError("glDrawArrays in drawToQuad");
	}

	private void drawToQuad(int texture, VertexBuffer buffer) {
		drawToQuad(texture, buffer, renderToQuadProgram);
	}

	public void destroy() {
		destroyOpenGL();
	}

	private void destroyOpenGL() {
		// Delete the shaders
		materialProgram.delete();

		Renderer.exitOnGLError("destroyOpenGL");
		
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
}
