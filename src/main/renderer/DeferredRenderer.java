package main.renderer;

import static main.log.ConsoleLogger.getLogger;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.SynchronousQueue;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import main.World;
import main.camera.Camera;
import main.model.DataChannels;
import main.model.Entity;
import main.model.EntityFactory;
import main.model.IEntity;
import main.model.Model;
import main.model.OBJLoader;
import main.model.QuadVertexBuffer;
import main.model.VertexBuffer;
import main.octree.Octree;
import main.renderer.command.Command;
import main.renderer.light.LightFactory;
import main.renderer.light.PointLight;
import main.renderer.light.Spotlight;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;
import main.renderer.material.MaterialFactory;
import main.shader.ComputeShaderProgram;
import main.shader.Program;
import main.texture.CubeMap;
import main.texture.TextureFactory;
import main.util.CLUtil;
import main.util.ressources.FileMonitor;
import main.util.stopwatch.GPUProfiler;
import main.util.stopwatch.GPUTaskProfile;
import main.util.stopwatch.OpenGLStopWatch;
import main.util.stopwatch.StopWatch;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.Sys;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL10GL;
import org.lwjgl.opencl.CLKernel;
import org.lwjgl.opencl.CLMem;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class DeferredRenderer implements Renderer {
	private static Logger LOGGER = getLogger();
	
	private BlockingQueue<Command> workQueue = new LinkedBlockingQueue<Command>();
	private Map<Command, SynchronousQueue<Result>> commandQueueMap = new ConcurrentHashMap<Command, SynchronousQueue<Result>>();

	public static EnumSet<DataChannels> RENDERTOQUAD = EnumSet.of(
			DataChannels.POSITION3,
			DataChannels.TEXCOORD);
	
	public int testTexture = -1;
	private OpenGLStopWatch glWatch;

	private FloatBuffer matrix44Buffer = BufferUtils.createFloatBuffer(16);
	private Program firstPassProgram;
	private Program secondPassPointProgram;
//	private ComputeShaderProgram secondPassPointComputeShaderProgram;
	private Program secondPassDirectionalProgram;
	private Program combineProgram;
	private static Program renderToQuadProgram;
	private Program lastUsedProgram = null;
	
	private CLKernel kernel;

//	private RenderTarget finalTarget;
	private RenderTarget firstPassTarget;
	private RenderTarget secondPassTarget;

	private VertexBuffer fullscreenBuffer;
	private VertexBuffer debugBuffer;

	private static float MINLIGHTRADIUS = 4.5f;
	private static float LIGHTRADIUSSCALE = 15f;
	private static int MAXLIGHTS = 100;
	public static List<PointLight> pointLights = new ArrayList<>();
	
	private IEntity sphere;

	private int deferredOutput;

	public CubeMap cubeMap;

	private OBJLoader objLoader;
	private EntityFactory entityFactory;
	private LightFactory lightFactory;
	private MaterialFactory materialFactory;
	private TextureFactory textureFactory;

	private Model sphereModel;

	private FloatBuffer identityMatrix44Buffer;

	private float secondPassScale = 0.5f;
	
	public DeferredRenderer(Spotlight light) {
		textureFactory = new TextureFactory();
		setupOpenGL();
		materialFactory = new MaterialFactory(this);
		setupShaders();
		objLoader = new OBJLoader(this);
		entityFactory = new EntityFactory(this);
		lightFactory = new LightFactory(this);
		
		sphereModel = null;
		try {
			sphereModel = objLoader.loadTexturedModel(new File("C:\\sphere.obj")).get(0);
			sphereModel.setMaterial(getMaterialFactory().getDefaultMaterial());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		Random randomGenerator = new Random();
		for (int i = 0; i < MAXLIGHTS; i++) {
			Material white = materialFactory.getMaterial(new HashMap<MAP,String>(){{
				put(MAP.DIFFUSE,"assets/textures/default.dds");
			}});
			Vector4f color = new Vector4f(randomGenerator.nextFloat(),randomGenerator.nextFloat(),randomGenerator.nextFloat(),1);
			Vector3f position = new Vector3f(i*randomGenerator.nextFloat()*2,randomGenerator.nextFloat(),i*randomGenerator.nextFloat());
			float range = MINLIGHTRADIUS + LIGHTRADIUSSCALE* randomGenerator.nextFloat();
			PointLight pointLight = lightFactory.getPointLight(position, sphereModel, color, range);
			pointLights.add(pointLight);
		}
	}

	private void setupOpenGL() {
		try {
			PixelFormat pixelFormat = new PixelFormat();
			ContextAttribs contextAtrributes = new ContextAttribs(4, 3)
				.withProfileCompatibility(true);
//				.withProfileCore(true);
			
			Display.setDisplayMode(new DisplayMode(WIDTH, HEIGHT));
			Display.setVSyncEnabled(false);
			Display.setTitle("DeferredRenderer");
			Display.create(pixelFormat, contextAtrributes);
			
			GL11.glViewport(0, 0, WIDTH, HEIGHT);
		} catch (LWJGLException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		GL11.glClearColor(0.4f, 0.6f, 0.9f, 0f);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_CULL_FACE);
//		GL11.glDisable(GL11.GL_CULL_FACE);
		
		// Map the internal OpenGL coordinate system to the entire screen
		GL11.glViewport(0, 0, WIDTH, HEIGHT);
		
		initIdentityMatrixBuffer();

//		finalTarget = new RenderTarget(WIDTH, HEIGHT, GL30.GL_RGBA32F, 1);
		firstPassTarget = new RenderTarget(WIDTH, HEIGHT, GL30.GL_RGBA32F, 4);
		secondPassTarget = new RenderTarget((int) (WIDTH * secondPassScale) , (int) (HEIGHT * secondPassScale), GL30.GL_RGBA32F, 2);

		fullscreenBuffer = new QuadVertexBuffer( true).upload();
		debugBuffer = new QuadVertexBuffer( false).upload();
		
		glWatch = new OpenGLStopWatch();
		
		try {
			cubeMap = textureFactory.getCubeMap("assets/textures/skybox.png");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		DeferredRenderer.exitOnGLError("setupOpenGL");
//		CLUtil.initialize();
	}
	
	private void initIdentityMatrixBuffer() {
		identityMatrix44Buffer = BufferUtils.createFloatBuffer(16);
		matrix44Buffer.rewind();
		new Matrix4f().store(matrix44Buffer);
		matrix44Buffer.rewind();
	}
	
	private void setupShaders() {
		
		firstPassProgram = new Program("/assets/shaders/deferred/first_pass_vertex.glsl", "/assets/shaders/deferred/first_pass_fragment.glsl", Entity.DEFAULTCHANNELS, true);

		secondPassPointProgram = new Program("/assets/shaders/deferred/second_pass_point_vertex.glsl", "/assets/shaders/deferred/second_pass_point_fragment.glsl", Entity.POSITIONCHANNEL, false);
//		secondPassPointComputeShaderProgram = new ComputeShaderProgram("/assets/shaders/deferred/second_pass_point_compute.glsl");
		secondPassDirectionalProgram = new Program("/assets/shaders/deferred/second_pass_directional_vertex.glsl", "/assets/shaders/deferred/second_pass_directional_fragment.glsl", Entity.POSITIONCHANNEL, false);

		combineProgram = new Program("/assets/shaders/deferred/combine_pass_vertex.glsl", "/assets/shaders/deferred/combine_pass_fragment.glsl", RENDERTOQUAD, false);
		renderToQuadProgram = new Program("/assets/shaders/passthrough_vertex.glsl", "/assets/shaders/simpletexture_fragment.glsl", RENDERTOQUAD);

		DeferredRenderer.exitOnGLError("setupShaders");
	}

	public void update(World world, float seconds) {
		try {
			executeCommands(world);
		} catch (Exception e) {
			e.printStackTrace();
		}
		FileMonitor.getInstance().checkAndNotify();
		updateLights(seconds);
		setLastFrameTime();
	}
	
	

	public void draw(Camera camera, Octree octree, List<IEntity> entities, Spotlight light) {
		GPUProfiler.startFrame();
		draw(null, octree, camera, entities, light);
	    GPUProfiler.endFrame();
//	    GPUTaskProfile tp;
//	    while((tp = GPUProfiler.getFrameResults()) != null){
//	        
//	        tp.dump(); //Dumps the frame to System.out.
//	    }
	}
	
	private void draw(RenderTarget target, Octree octree, Camera camera, List<IEntity> entities, Spotlight light) {

		GPUProfiler.start("First pass");
		drawFirstPass(camera, octree);
		GPUProfiler.end();

		GPUProfiler.start("Second pass");
		drawSecondPass(camera, light, pointLights);
		GPUProfiler.end();
		
		GL11.glViewport(0, 0, WIDTH, HEIGHT);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

		GL11.glDisable(GL11.GL_DEPTH_TEST);

		GPUProfiler.start("Combine pass");
		combinePass(target, firstPassTarget, secondPassTarget);
		GPUProfiler.end();
//		drawToQuad(secondPassTarget.getRenderedTexture(), fullscreenBuffer);

		
		if (World.DEBUGFRAME_ENABLED) {
			drawToQuad(firstPassTarget.getRenderedTexture(3), debugBuffer);
		}
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		
	}

	private void drawFirstPass(Camera camera, Octree octree) {
		GL11.glEnable(GL11.GL_CULL_FACE);
		firstPassProgram.use();
		GL11.glDepthMask(true);
		firstPassTarget.use(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);

		List<IEntity> entities = new ArrayList<>();
		if (World.useFrustumCulling) {
			entities.addAll(octree.getVisible(camera));
			
//			System.out.println("Visible: " + entities.size() + " / " + octree.getEntities().size() + " / " + octree.getEntityCount());
			for (int i = 0; i < entities.size(); i++) {
				if (!entities.get(i).isInFrustum(camera)) {
					entities.remove(i);
				}
			}
//			System.out.println("Visible exactly: " + entities.size() + " / " + octree.getEntities().size());
			
		} else {
			entities.addAll(octree.getEntities());
		}

		for (IEntity entity : entities) {
			entity.draw(this, camera);
		}
		
		for (IEntity entity : entities.stream().filter(entity -> { return entity.isSelected(); }).collect(Collectors.toList())) {
			Material old = entity.getMaterial();
			entity.setMaterial(materialFactory.getDefaultMaterial());
			entity.draw(this, camera);
			entity.setMaterial(old);			
		}
		
		
		if (World.DRAWLIGHTS_ENABLED) {
			for (PointLight light : pointLights) {
				if (!light.isInFrustum(camera)) { continue;}
				light.drawAsMesh(this, camera);
			}
		}
		
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
	}

	private void drawSecondPass(Camera camera, Spotlight directionalLight, List<PointLight> pointLights) {

		GPUProfiler.start("Directional light");
		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL11.glBlendFunc(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE);

//		finalTarget.use(true);
		secondPassTarget.use(true);
		GL11.glClearColor(0,0,0,0);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

		secondPassDirectionalProgram.use();

		GPUProfiler.start("Activate GBuffer textures");
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(0)); // position
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(1)); // normal, depth
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(2)); // color, reflectiveness
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 3);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(3)); // specular
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 4);
		cubeMap.bind();
		GPUProfiler.end();

		GPUProfiler.start("Set uniforms");
		secondPassDirectionalProgram.setUniform("eyePosition", camera.getPosition());
		secondPassDirectionalProgram.setUniform("useAmbientOcclusion", World.useAmbientOcclusion);
		secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", World.AMBIENTOCCLUSION_RADIUS);
		secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", World.AMBIENTOCCLUSION_TOTAL_STRENGTH);
		secondPassDirectionalProgram.setUniform("screenWidth", (float) WIDTH);
		secondPassDirectionalProgram.setUniform("screenHeight", (float) HEIGHT);
		secondPassDirectionalProgram.setUniform("secondPassScale", secondPassScale);
		secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		secondPassDirectionalProgram.setUniform("lightDirection", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z);
		secondPassDirectionalProgram.setUniform("lightDiffuse", directionalLight.getColor());
//		LOGGER.log(Level.INFO, String.format("DIR LIGHT: %f %f %f", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z));
		GPUProfiler.end();
		GPUProfiler.start("Draw fullscreen buffer");
		fullscreenBuffer.draw();
		GPUProfiler.end();

		GPUProfiler.end();
		GPUProfiler.start("Pointlights");
		secondPassPointProgram.use();

		GPUProfiler.start("Set shared uniforms");
//		secondPassPointProgram.setUniform("lightCount", pointLights.size());
//		secondPassPointProgram.setUniformAsBlock("pointlights", PointLight.convert(pointLights));
		secondPassPointProgram.setUniform("screenWidth", (float) WIDTH);
		secondPassPointProgram.setUniform("screenHeight", (float) HEIGHT);
		secondPassPointProgram.setUniform("secondPassScale", secondPassScale);
		secondPassPointProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		secondPassPointProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		GPUProfiler.end();

		GPUProfiler.start("Draw lights");
		boolean firstLightDrawn = false;
		for (int i = 0 ; i < pointLights.size(); i++) {
			PointLight light = pointLights.get(i);
//			if(!light.isInFrustum(camera)) {
//				continue;
//			}
			
			Vector3f distance = new Vector3f();
			Vector3f.sub(camera.getPosition(), light.getPosition(), distance);
			float lightRadius = light.getRadius();
			
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
			
			if(firstLightDrawn) {
				light.drawAgain(this, secondPassPointProgram);
			} else {
				light.draw(this, secondPassPointProgram);
			}
			firstLightDrawn = true;
		}
		secondPassTarget.unuse();
//		finalTarget.unuse();
		GL11.glDisable(GL11.GL_BLEND);
		GPUProfiler.end();
		GPUProfiler.end();
	}


	private void combinePass(RenderTarget target, RenderTarget gBuffer, RenderTarget laBuffer) {
		combineProgram.use();
		combineProgram.setUniform("screenWidth", (float) WIDTH);
		combineProgram.setUniform("screenHeight", (float) HEIGHT);
//		combineProgram.setUniform("secondPassScale", secondPassScale);
		combineProgram.setUniform("ambientColor", World.AMBIENT_LIGHT);
		
		if(target == null) {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		} else {
			target.use(true);
		}
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, gBuffer.getRenderedTexture(2)); // color, reflectiveness
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, laBuffer.getRenderedTexture(0)); // light accumulation
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, laBuffer.getRenderedTexture(1)); // ao, reflection
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 3);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(3)); // specular
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 4);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(0)); // position, glossiness
		
		fullscreenBuffer.draw();

//		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer) {
		drawToQuad(texture, buffer, renderToQuadProgram);
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer, Program program) {
		program.use();

		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

		buffer.draw();
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

	private static long lastFrameTime = 0l;
	private static void setLastFrameTime() {
		Display.setTitle("FPS: " + (int)(1000/getDeltainMS()));
		lastFrameTime = getTime();
	}
	private static long getTime() {
		return (Sys.getTime() * 1000) / Sys.getTimerResolution();
	}
	public static double getDeltainMS() {
		long currentTime = getTime();
		double delta = (double) currentTime - (double) lastFrameTime;
		return delta;
	}
	public static double getDeltainS() {
		return (getDeltainMS() / 1000d);
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

	@Override
	public float getElapsedSeconds() {
		return (float)getDeltainS();
	}

	@Override
	public void drawDebug(Camera camera, Octree octree, List<IEntity> entities, Spotlight light) {
		///////////// firstpass
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
		
		List<IEntity> visibleEntities = new ArrayList<>();
		if (World.useFrustumCulling) {
			visibleEntities.addAll(octree.getVisible(camera));
//			entities.addAll(octree.getEntities());
			for (int i = 0; i < visibleEntities.size(); i++) {
				if (!visibleEntities.get(i).isInFrustum(camera)) {
					visibleEntities.remove(i);
				}
			}
		} else {
			visibleEntities.addAll(octree.getEntities());
		}
		
		for (IEntity entity : visibleEntities) {
			entity.drawDebug(firstPassProgram);
		}
		if (World.DRAWLIGHTS_ENABLED) {
			for (IEntity entity : pointLights) {
				entity.drawDebug(firstPassProgram);
			}	
		}
		
		if (Octree.DRAW_LINES) {
			octree.drawDebug(this, camera, firstPassProgram);
		}

	    drawLine(new Vector3f(), new Vector3f(15,0,0));
	    drawLine(new Vector3f(), new Vector3f(0,15,0));
	    drawLine(new Vector3f(), new Vector3f(0,0,-15));
	    drawLine(new Vector3f(), (Vector3f) ((Vector3f)(camera.getViewDirection().negate())).scale(15));
	    drawLine(new Vector3f(), (Vector3f) ((Vector3f)(camera.getViewDirection().negate())).scale(15));
	    drawLine(new Vector3f(), (Vector3f) ((Vector3f)(camera.getViewDirection().negate())).scale(15));
		drawLines(firstPassProgram);
		
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		////////////////////
		
		drawSecondPass(camera, light, pointLights);
		
		GL11.glViewport(0, 0, WIDTH, HEIGHT);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
	    
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		drawToQuad(firstPassTarget.getRenderedTexture(2), fullscreenBuffer);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		
	}
	

	private void drawLines(Program program) {

		program.setUniform("materialDiffuseColor", new Vector3f(1,0,0));
		float[] points = new float[linePoints.size() * 3];
		for (int i = 0; i < linePoints.size(); i++) {
			Vector3f point = linePoints.get(i);
			points[3*i + 0] = point.x;
			points[3*i + 1] = point.y;
			points[3*i + 2] = point.z;
		}
		VertexBuffer buffer = new VertexBuffer(points, EnumSet.of(DataChannels.POSITION3)).upload();
		buffer.drawDebug();
		linePoints.clear();
	}

	@Override
	public void drawLine(Vector3f from, Vector3f to) {
		linePoints.add(from);
		linePoints.add(to);
	}

	@Override
	public Program getLastUsedProgram() {
		return lastUsedProgram;
	}

	@Override
	public void setLastUsedProgram(Program program) {
		this.lastUsedProgram = program;
	}

	@Override
	public CubeMap getEnvironmentMap() {
		return cubeMap;
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
	ForkJoinPool fjpool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()*2);

	private List<Vector3f> linePoints = new ArrayList<>();

	private void updateLights(float seconds) {
//		for (PointLight light : pointLights) {
//			double sinusX = 10f*Math.sin(100000000f/System.currentTimeMillis());
//			double sinusY = 1f*Math.sin(100000000f/System.currentTimeMillis());
//			light.move(new Vector3f((float)sinusX,(float)sinusY, 0f));
//			light.update();
//		}
		 RecursiveAction task = new RecursiveUpdate(pointLights, 0, pointLights.size(), seconds);
//         long start = System.currentTimeMillis();
         fjpool.invoke(task);
//         System.out.println("Parallel processing time: "    + (System.currentTimeMillis() - start)+ " ms");
	}
	
	private class RecursiveUpdate extends RecursiveAction {
		final int LIMIT = 3;
		int result;
		int start, end;
		List<PointLight> lights;
		private float seconds;

		RecursiveUpdate(List<PointLight> lights, int start, int end, float seconds) {
			this.start = start;
			this.end = end;
			this.lights = lights;
			this.seconds = seconds;
		}
		
		@Override
		protected void compute() {
			if ((end - start) < LIMIT) {
				for (int i = start; i < end; i++) {
					double x =  Math.sin(System.currentTimeMillis() / 1000);
					double z =  Math.cos(System.currentTimeMillis() / 1000);
					lights.get(i).move(new Vector3f((float)x , 0, (float)z));
					lights.get(i).update(1);
				}
			} else {
				int mid = (start + end) / 2;
				RecursiveUpdate left = new RecursiveUpdate(lights, start, mid, seconds);
				RecursiveUpdate right = new RecursiveUpdate(lights, mid, end, seconds);
				left.fork();
				right.fork();
				left.join();
				right.join();
			}
		}
		
	}

	@Override
	public MaterialFactory getMaterialFactory() {
		return materialFactory;
	}

	@Override
	public TextureFactory getTextureFactory() {
		return textureFactory;
	}

	@Override
	public OBJLoader getOBJLoader() {
		return objLoader;
	}

	@Override
	public EntityFactory getEntityFactory() {
		return entityFactory;
	}

	@Override
	public Model getSphere() {
		return sphereModel;
	}
	
	public SynchronousQueue<Result> addCommand(Command command) {
	    SynchronousQueue<Result> queue = new SynchronousQueue<Result>();
	    commandQueueMap.put(command, queue);
	    workQueue.offer(command);
	    return queue;
	}

	private void executeCommands(World world) throws Exception {
        Command command = workQueue.poll();
        while(command != null) {
        	Result result = command.execute(world);
            SynchronousQueue<Result> queue = commandQueueMap.get(command);
            queue.offer(result);
        	command = workQueue.poll();
        }
	}

//	@Override
//	public IEntity getSphere() {
//		return sphere;
//	}

//	private void tiledDeferredPass(Camera camera, Spotlight light, List<PointLight> pointLights) {
//		secondPassPointComputeShaderProgram.use();
//
////		GL13.glActiveTexture(GL13.GL_TEXTURE0);
////		GL11.glBindTexture(GL11.GL_TEXTURE_2D, deferredOutput); // output map
////		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
////		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(0)); // position map
////		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
////		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(1)); // normal map
////		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 3);
////		GL11.glBindTexture(GL11.GL_TEXTURE_2D, firstPassTarget.getRenderedTexture(2)); // albedo map
//
//		GL42.glBindImageTexture(0, deferredOutput, 0, false, 0, GL15.GL_WRITE_ONLY, GL30.GL_RGBA32F);
//		//GL20.glUniform1i(GL20.glGetUniformLocation(secondPassPointComputeShaderProgram.getId(),"outTexture"), deferredOutput);
//		secondPassPointComputeShaderProgram.dispatchCompute(16, 16, 1);
//
////		finalTarget.saveBuffer("C:\\" +  System.currentTimeMillis() + ".png");
////		System.exit(-1);
//		
//	}
}
