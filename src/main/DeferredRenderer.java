package main;

import static main.log.ConsoleLogger.getLogger;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.logging.Logger;

import main.shader.ComputeShaderProgram;
import main.shader.Program;
import main.util.CLUtil;
import main.util.OBJLoader;
import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.input.Mouse;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL10GL;
import org.lwjgl.opencl.CLKernel;
import org.lwjgl.opencl.CLMem;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class DeferredRenderer implements Renderer {
	private static Logger LOGGER = getLogger();

	public static EnumSet<DataChannels> RENDERTOQUAD = EnumSet.of(
			DataChannels.POSITION3,
			DataChannels.TEXCOORD);
	
	public int testTexture = -1;

	private int fps;
	private long lastFPS;
	private long lastFrame;

	private FloatBuffer matrix44Buffer = BufferUtils.createFloatBuffer(16);
	private Program firstPassProgram;
	private Program secondPassPointProgram;
	private ComputeShaderProgram secondPassPointComputeShaderProgram;
	private Program secondPassDirectionalProgram;
	private Program combineProgram;
	private static Program renderToQuadProgram;
	
	private CLKernel kernel;

	private RenderTarget finalTarget;
	private RenderTarget firstPassTarget;
	private RenderTarget secondPassTarget;

	private VertexBuffer fullscreenBuffer;
	private VertexBuffer debugBuffer;

	private static float MINLIGHTRADIUS = 0.5f;
	private static float LIGHTRADIUSSCALE = 1f;
	private static int MAXLIGHTS = 256;
	public static List<PointLight> pointLights = new ArrayList<>();
	
	private IEntity sphere;

	private int deferredOutput;
	
	public DeferredRenderer(Spotlight light) {
		setupOpenGL();
		setupShaders();
		getDelta();
		lastFPS = Util.getTime();
		Mouse.setCursorPosition(WIDTH/2, HEIGHT/2);
		
		Model sphereModel = null;
		try {
			sphereModel = OBJLoader.loadTexturedModel(new File("C:\\sphere.obj")).get(0);
			sphere = new Entity(this, sphereModel);
			Vector3f scale = new Vector3f(0.5f, 0.5f, 0.5f);
			scale.scale(new Random().nextFloat()*10);
			sphere.setScale(scale);
			sphere.update();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Random random = new Random();
		for (int i = 0; i < MAXLIGHTS; i++) {
			float randomFloatX = (random.nextFloat() -0.5f) * 20f;
			float randomFloatY = (random.nextFloat() -0.5f) * 2;
			float randomFloatZ = (random.nextFloat() -0.5f) * 20f;
			Vector3f position = new Vector3f();
			position.x += randomFloatX;
			position.y -= randomFloatY;
			position.z += randomFloatZ;
			PointLight pointLight = new PointLight(this, sphereModel, position);
			pointLight.setScale(MINLIGHTRADIUS + LIGHTRADIUSSCALE* random.nextFloat());
			pointLight.setColor(new Vector4f(random.nextFloat(),random.nextFloat(),random.nextFloat(),random.nextFloat()));
			pointLights.add(pointLight);
		}
	}

	private void setupOpenGL() {
		try {
			PixelFormat pixelFormat = new PixelFormat();
			ContextAttribs contextAtrributes = new ContextAttribs(4, 3);
//				.withForwardCompatible(true)
//				.withProfileCore(true);
			
			Display.setDisplayMode(new DisplayMode(WIDTH, HEIGHT));
			Display.setVSyncEnabled(false);
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
		firstPassTarget = new RenderTarget(WIDTH, HEIGHT, GL30.GL_RGBA32F, 4);
		secondPassTarget = new RenderTarget(WIDTH, HEIGHT, GL30.GL_RGB32F, 0.0f, 0.0f, 0.0f, 0f, GL11.GL_LINEAR, 1);

//		deferredOutput = GL11.glGenTextures();
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, deferredOutput);
//		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
//		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
//
//		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
//		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
//		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGB32F, WIDTH, HEIGHT, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, BufferUtils.createFloatBuffer(WIDTH * HEIGHT));
//		
		
		fullscreenBuffer = new QuadVertexBuffer( true).upload();
		debugBuffer = new QuadVertexBuffer( false).upload();
		
		DeferredRenderer.exitOnGLError("setupOpenGL");
		CLUtil.initialize();
	}
	


	private void setupShaders() {
		
		firstPassProgram = new Program("/assets/shaders/deferred/first_pass_vertex.glsl", "/assets/shaders/deferred/first_pass_fragment.glsl", Entity.DEFAULTCHANNELS, true);

		secondPassPointProgram = new Program("/assets/shaders/deferred/second_pass_point_vertex.glsl", "/assets/shaders/deferred/second_pass_point_fragment.glsl", Entity.POSITIONCHANNEL, false);
		secondPassPointComputeShaderProgram = new ComputeShaderProgram("/assets/shaders/deferred/second_pass_point_compute.glsl");
		secondPassDirectionalProgram = new Program("/assets/shaders/deferred/second_pass_directional_vertex.glsl", "/assets/shaders/deferred/second_pass_directional_fragment.glsl", Entity.POSITIONCHANNEL, false);

		combineProgram = new Program("/assets/shaders/deferred/combine_pass_vertex.glsl", "/assets/shaders/deferred/combine_pass_fragment.glsl", RENDERTOQUAD, false);
		renderToQuadProgram = new Program("/assets/shaders/passthrough_vertex.glsl", "/assets/shaders/simpletexture_fragment.glsl", RENDERTOQUAD);

//		String kernelString = Util.loadAsTextFile("/assets/shaders/deferred/second_pass_point.cls");
//		CLProgram sumProgram = CL10.clCreateProgramWithSource(CLUtil.context, kernelString, null);
//		
//		kernel = CLUtil.build(sumProgram, "main");
		
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
		updateLights();
	}
	
	ForkJoinPool fjpool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()*2);
	private void updateLights() {
//		for (PointLight light : pointLights) {
//			double sinusX = 10f*Math.sin(100000000f/System.currentTimeMillis());
//			double sinusY = 1f*Math.sin(100000000f/System.currentTimeMillis());
//			light.move(new Vector3f((float)sinusX,(float)sinusY, 0f));
//			light.update();
//		}
		 RecursiveAction task = new RecursiveUpdate(pointLights, 0, pointLights.size());
//         long start = System.currentTimeMillis();
         fjpool.invoke(task);
//         System.out.println("Parallel processing time: "    + (System.currentTimeMillis() - start)+ " ms");
	}
	
	private class RecursiveUpdate extends RecursiveAction {
		final int LIMIT = 3;
		int result;
		int start, end;
		List<PointLight> lights;

		RecursiveUpdate(List<PointLight> lights, int start, int end) {
			this.start = start;
			this.end = end;
			this.lights = lights;
		}
		
		@Override
		protected void compute() {
			if ((end - start) < LIMIT) {
				for (int i = start; i < end; i++) {
					double sinusX = 10f*Math.sin(100000000f/System.currentTimeMillis());
					double sinusY = 1f*Math.sin(100000000f/System.currentTimeMillis());
					lights.get(i).move(new Vector3f((float)sinusX,(float)sinusY, 0f));
					lights.get(i).update();
				}
			} else {
				int mid = (start + end) / 2;
				RecursiveUpdate left = new RecursiveUpdate(lights, start, mid);
				RecursiveUpdate right = new RecursiveUpdate(lights, mid, end);
				left.fork();
				right.fork();
				left.join();
				right.join();
			}
		}
		
	}

	public void draw(Camera camera, List<IEntity> entities, Spotlight light) {
		draw(finalTarget, camera, entities, light);
	}
	
	private void draw(RenderTarget target, Camera camera, List<IEntity> entities, Spotlight light) {

		drawFirstPass(camera, entities);
		drawSecondPass(camera, light, pointLights);
		combinePass(finalTarget, firstPassTarget, secondPassTarget);
//		openClTest(finalTarget);
//		tiledDeferredPass(camera, light, pointLights);
		
		GL11.glViewport(0, 0, WIDTH, HEIGHT);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

		GL11.glDisable(GL11.GL_DEPTH_TEST);
		drawToQuad(finalTarget.getRenderedTexture(), fullscreenBuffer);
		if (World.DEBUGFRAME_ENABLED) {
			drawToQuad(firstPassTarget.getRenderedTexture(2), debugBuffer);
//			drawToQuad(deferredOutput, debugBuffer);
		}
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}

	private void tiledDeferredPass(Camera camera, Spotlight light, List<PointLight> pointLights) {
		secondPassPointComputeShaderProgram.use();

//		GL13.glActiveTexture(GL13.GL_TEXTURE0);
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, deferredOutput); // output map
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(0)); // position map
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(1)); // normal map
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 3);
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(2)); // albedo map

		GL42.glBindImageTexture(0, deferredOutput, 0, false, 0, GL15.GL_WRITE_ONLY, GL30.GL_RGBA32F);
		//GL20.glUniform1i(GL20.glGetUniformLocation(secondPassPointComputeShaderProgram.getId(),"outTexture"), deferredOutput);
		secondPassPointComputeShaderProgram.dispatchCompute(16, 16, 1);

//		finalTarget.saveBuffer("C:\\" +  System.currentTimeMillis() + ".png");
//		System.exit(-1);
		
	}

	private void openClTest(RenderTarget target) {
		GL11.glFinish();
		IntBuffer errorBuffer = BufferUtils.createIntBuffer(1);
		
		
		CLMem memSourceImage = org.lwjgl.opencl.CL10GL.clCreateFromGLTexture2D(CLUtil.context, CL10.CL_READ_ONLY_CACHE, GL11.GL_TEXTURE_2D, 0, firstPassTarget.getRenderedTexture(), errorBuffer);
		CLMem memTargetImage = org.lwjgl.opencl.CL10GL.clCreateFromGLTexture2D(CLUtil.context, CL10.CL_MEM_WRITE_ONLY, GL11.GL_TEXTURE_2D, 0, target.getRenderedTexture(), errorBuffer);
		int x = 0;
		
		int error = CL10GL.clEnqueueAcquireGLObjects(CLUtil.queue, memTargetImage, null, null);
		
		
		kernel.setArg(0, memSourceImage);
		kernel.setArg(1, memTargetImage);

		final int dimensions = 1;
		PointerBuffer globalWorkSize = BufferUtils.createPointerBuffer(dimensions);
		globalWorkSize.put(0, 10);

		CL10.clEnqueueNDRangeKernel(CLUtil.queue, kernel, dimensions, null, globalWorkSize, null, null, null);

		FloatBuffer writeTo = BufferUtils.createFloatBuffer(200);
		CL10.clEnqueueReadBuffer(CLUtil.queue, memTargetImage, CL10.CL_TRUE, 0, writeTo, null, null);
		float[] result = new float[200];
		writeTo.get(result, 0, 200);
		CL10.clFinish(CLUtil.queue);
	}

	private void drawFirstPass(Camera camera, List<IEntity> entities) {
		GL11.glEnable(GL11.GL_CULL_FACE);
		firstPassProgram.use();
		GL11.glDepthMask(true);
		firstPassTarget.use(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
		firstPassProgram.setUniform("screenWidth", (float) WIDTH);
		firstPassProgram.setUniform("screenHeight", (float) HEIGHT);
		firstPassProgram.setUniform("useParallax", World.useParallax);
		firstPassProgram.setUniform("useSteepParallax", World.useSteepParallax);
		firstPassProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		firstPassProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		firstPassProgram.setUniform("eyePosition", camera.getPosition());
		for (IEntity entity : entities) {
			if ((World.useFrustumCulling && entity.isInFrustum(camera)) || !World.useFrustumCulling) {
				entity.draw(firstPassProgram);
			}
		}
		if (World.DRAWLIGHTS_ENABLED) {
			for (IEntity entity : pointLights) {
				entity.draw(firstPassProgram);
			}	
		}
		
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
	}

	private void drawSecondPass(Camera camera, Spotlight directionalLight, List<PointLight> pointLights) {

		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);

		secondPassTarget.use(false);
		//GL11.glClearColor(0.15f,0.15f,0.15f,0.15f); // ambient light
		GL11.glClearColor(0,0,0,0);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

		secondPassDirectionalProgram.use();
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(0));
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(1));
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(2));

		secondPassDirectionalProgram.setUniform("screenWidth", (float) WIDTH);
		secondPassDirectionalProgram.setUniform("screenHeight", (float) HEIGHT);
		secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		secondPassDirectionalProgram.setUniform("lightDirection", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z);
//		LOGGER.log(Level.INFO, String.format("DIR LIGHT: %f %f %f", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z));
		fullscreenBuffer.draw();

		secondPassPointProgram.use();
		
//		secondPassPointProgram.setUniform("lightCount", pointLights.size());
//		secondPassPointProgram.setUniformAsBlock("pointlights", PointLight.convert(pointLights));
		secondPassPointProgram.setUniform("screenWidth", (float) WIDTH);
		secondPassPointProgram.setUniform("screenHeight", (float) HEIGHT);
		secondPassPointProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		secondPassPointProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		
		for (int i = 0 ; i < pointLights.size(); i++) {
			PointLight light = pointLights.get(i);
			Vector3f distance = new Vector3f();
			Vector3f.sub(camera.getPosition(), light.getPosition(), distance);
			float lightRadius = light.getScale().x;
			
			//TODO: Check this....
			// camera is inside light range
			if (distance.length() < lightRadius) {
				GL11.glDisable(GL11.GL_CULL_FACE);
			// camera is outside light range, cull back sides
			} else {
				GL11.glEnable(GL11.GL_CULL_FACE);
				GL11.glCullFace(GL11.GL_BACK);
			}

//			secondPassPointProgram.setUniform("currentLightIndex", i);
			secondPassPointProgram.setUniform("lightPosition", light.getPosition());
			secondPassPointProgram.setUniform("lightRadius", lightRadius);
			secondPassPointProgram.setUniform("lightDiffuse", light.getColor().x, light.getColor().y, light.getColor().z);
			secondPassPointProgram.setUniform("lightSpecular", light.getColor().x, light.getColor().y, light.getColor().z);
			light.draw(secondPassPointProgram);
		}
		secondPassTarget.unuse();
		GL11.glDisable(GL11.GL_BLEND);
	}


	private void combinePass(RenderTarget target, RenderTarget gBuffer, RenderTarget laBuffer) {
		combineProgram.use();
		combineProgram.setUniform("screenWidth", (float) WIDTH);
		combineProgram.setUniform("screenHeight", (float) HEIGHT);
		combineProgram.setUniform("useAmbientOcclusion", World.useAmbientOcclusion);
		combineProgram.setUniform("ambientOcclusionRadius", World.AMBIENTOCCLUSION_RADIUS);
		combineProgram.setUniform("ambientOcclusionTotalStrength", World.AMBIENTOCCLUSION_TOTAL_STRENGTH);
		combineProgram.setUniform("ambientOcclusionStrength", World.AMBIENTOCCLUSION_STRENGTH);
		combineProgram.setUniform("ambientOcclusionFalloff", World.AMBIENTOCCLUSION_FALLOFF);
		target.use(true);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(2)); // albedo
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, secondPassTarget.getRenderedTexture(0)); // light accumulation
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(1)); // normal
		
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
