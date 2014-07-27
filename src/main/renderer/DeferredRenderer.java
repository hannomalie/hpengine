package main.renderer;

import static main.log.ConsoleLogger.getLogger;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
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
import main.shader.Program;
import main.shader.ProgramFactory;
import main.texture.CubeMap;
import main.texture.DynamicCubeMap;
import main.texture.TextureFactory;
import main.util.Util;
import main.util.ressources.FileMonitor;
import main.util.stopwatch.GPUProfiler;
import main.util.stopwatch.GPUTaskProfile;
import main.util.stopwatch.OpenGLStopWatch;

import org.apache.commons.io.FileUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.Sys;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.bulletphysics.dynamics.DynamicsWorld;

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
	private static Program renderToQuadProgram;
	private Program lastUsedProgram = null;
	
	private VertexBuffer fullscreenBuffer;
	private VertexBuffer debugBuffer;

	private static float MINLIGHTRADIUS = 4.5f;
	private static float LIGHTRADIUSSCALE = 15f;
	private static int MAXLIGHTS = 50;
	public static List<PointLight> pointLights = new ArrayList<>();
	
	private IEntity sphere;

	public CubeMap cubeMap;

	private OBJLoader objLoader;
	private ProgramFactory programFactory;
	private EntityFactory entityFactory;
	private LightFactory lightFactory;
	private MaterialFactory materialFactory;
	private TextureFactory textureFactory;

	private Model sphereModel;

	private FloatBuffer identityMatrix44Buffer;

	private GBuffer gBuffer;

	private Program cubeMapDiffuseProgram;
	
	public DeferredRenderer(Spotlight light) {
		setupOpenGL();
		textureFactory = new TextureFactory();
		DeferredRenderer.exitOnGLError("After TextureFactory");
		programFactory = new ProgramFactory(this);
		setupShaders();
		setUpGBuffer();
		materialFactory = new MaterialFactory(this);
		objLoader = new OBJLoader(this);
		entityFactory = new EntityFactory(this);
		lightFactory = new LightFactory(this);
		Quaternion cubeMapCamInitialOrientation = new Quaternion();
		Quaternion.setIdentity(cubeMapCamInitialOrientation);
		cubeMapCam.setOrientation(cubeMapCamInitialOrientation);
		cubeMapCam.rotate(new Vector4f(0,1,0,90));
		
		sphereModel = null;
		try {
			sphereModel = objLoader.loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sphere.obj")).get(0);
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

	private void setUpGBuffer() {

		Program firstPassProgram = programFactory.getProgram("first_pass_vertex.glsl", "first_pass_fragment.glsl");
		Program secondPassPointProgram = programFactory.getProgram("second_pass_point_vertex.glsl", "second_pass_point_fragment.glsl", Entity.POSITIONCHANNEL, false);
		Program secondPassDirectionalProgram = programFactory.getProgram("second_pass_directional_vertex.glsl", "second_pass_directional_fragment.glsl", Entity.POSITIONCHANNEL, false);
		Program combineProgram = programFactory.getProgram("combine_pass_vertex.glsl", "combine_pass_fragment.glsl", RENDERTOQUAD, false);

		gBuffer = new GBuffer(this, firstPassProgram, secondPassDirectionalProgram, secondPassPointProgram, combineProgram);

		cubeMapDiffuseProgram = programFactory.getProgram("first_pass_vertex.glsl", "cubemap_fragment.glsl");
		cubeMapRenderTargets = new CubeRenderTarget[6];
		for(int i = 0; i < 6; i++) {
			cubeMapRenderTargets[i] = new CubeRenderTarget(cubeMap.getImageWidth(), cubeMap.getImageHeight(), cubeMap.getTextureID(), i);
		}
		
		DeferredRenderer.exitOnGLError("setupGBuffer");
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

		fullscreenBuffer = new QuadVertexBuffer( true).upload();
		debugBuffer = new QuadVertexBuffer( false).upload();
		
		glWatch = new OpenGLStopWatch();
		
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
		DeferredRenderer.exitOnGLError("Before setupShaders");
//		try {
//			cubeMap = textureFactory.getCubeMap("assets/textures/skybox.png");
			cubeMap = new DynamicCubeMap(1024, 1024);
//			DeferredRenderer.exitOnGLError("setup cubemap");
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		
		copyDefaultShaders();

		renderToQuadProgram = programFactory.getProgram("passthrough_vertex.glsl", "simpletexture_fragment.glsl", RENDERTOQUAD, false);

		DeferredRenderer.exitOnGLError("setupShaders");
	}

	private void copyDefaultShaders() {
		copyShaderIfNotExists(ProgramFactory.FIRSTPASS_DEFAULT_FRAGMENTSHADER_FILE);
		copyShaderIfNotExists(ProgramFactory.FIRSTPASS_DEFAULT_VERTEXSHADER_FILE);
		copyShaderIfNotExists("second_pass_directional_fragment.glsl");
		copyShaderIfNotExists("second_pass_directional_vertex.glsl");
		copyShaderIfNotExists("second_pass_point_fragment.glsl");
		copyShaderIfNotExists("second_pass_point_vertex.glsl");
		copyShaderIfNotExists("combine_pass_vertex.glsl");
		copyShaderIfNotExists("combine_pass_fragment.glsl");
		copyShaderIfNotExists("passthrough_vertex.glsl");
		copyShaderIfNotExists("simpletexture_fragment.glsl");
		copyShaderIfNotExists("shadowmap_fragment.glsl");
		copyShaderIfNotExists("mvp_vertex.glsl");
		
	}

	private void copyShaderIfNotExists(String fileName) {
		File fromFile = new File("assets/shaders/deferred/" + fileName);
		File toFile = new File(Program.getDirectory() + fileName);
		
		if (!toFile.exists()) {
			try {
				FileUtils.copyFile(fromFile, toFile);
			} catch (IOException e) {
				e.printStackTrace();
			}	
		}
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
		fpsCounter.update(seconds);
	}
	
	

	public void draw(Camera camera, Octree octree, List<IEntity> entities, Spotlight light) {
		GPUProfiler.startFrame();
		draw(null, octree, camera, entities, light);
	    GPUProfiler.endFrame();
	    GPUTaskProfile tp;
	    while((tp = GPUProfiler.getFrameResults()) != null){
	        
	        tp.dump(); //Dumps the frame to System.out.
	    }
	}
	
	private void draw(RenderTarget target, Octree octree, Camera camera, List<IEntity> entities, Spotlight light) {

		GPUProfiler.start("First pass");
		gBuffer.drawFirstPass(camera, octree, pointLights);
		GPUProfiler.end();

		GPUProfiler.start("Draw a cubemap");
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		drawCubeMap(octree, camera);
		GPUProfiler.end();
		
		GPUProfiler.start("Shadowmap pass");
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		light.drawShadowMap(octree);
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GPUProfiler.end();

		GPUProfiler.start("Second pass");
		gBuffer.drawSecondPass(camera, light, pointLights, cubeMap);
		GPUProfiler.end();
		
		GL11.glViewport(0, 0, WIDTH, HEIGHT);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

		GL11.glDisable(GL11.GL_DEPTH_TEST);

		GPUProfiler.start("Combine pass");
		gBuffer.combinePass(target, light, camera);
		GPUProfiler.end();
//		drawToQuad(secondPassTarget.getRenderedTexture(), fullscreenBuffer);

		
		if (World.DEBUGFRAME_ENABLED) {
			drawToQuad(light.getShadowMapId(), debugBuffer);
		}
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		
	}

	
	private void drawCubeMap(Octree octree, Camera camera) {
//		cubeMapCam.setPosition(camera.getPosition());
//		cubeMapCam.setOrientation(camera.getOrientation());
		
		drawCubeMapSides(cubeMapRenderTargets, octree, cubeMapCam);
//		cubeMap.bind();
//		renderTarget.saveBuffer(""+System.currentTimeMillis()+".png");
	}

	private void drawCubeMapSides(RenderTarget[] renderTargets, Octree octree, Camera camera) {
		Quaternion initialOrientation = camera.getOrientation();
		Vector3f initialPosition = camera.getPosition();
		
		cubeMapDiffuseProgram.use();
		for(int i = 0; i < 6; i++) {
			rotateForIndex(i, camera);
			List<IEntity> visibles = octree.getEntities();
			renderTargets[i].use(true);
			FloatBuffer viewMatrixAsBuffer = camera.getViewMatrixAsBuffer();
			FloatBuffer projectionMatrixAsBuffer = camera.getProjectionMatrixAsBuffer();
			cubeMapDiffuseProgram.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
			cubeMapDiffuseProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
			
			for (IEntity e : visibles) {
				entityBuffer.rewind();
				e.getModelMatrix().store(entityBuffer);
				entityBuffer.rewind();
				cubeMapDiffuseProgram.setUniformAsMatrix4("modelMatrix", entityBuffer);
				e.getMaterial().setTexturesActive(cubeMapDiffuseProgram);
				e.getVertexBuffer().draw();
			}
			
		}
		camera.setPosition(initialPosition);
		camera.setOrientation(initialOrientation);
	}
	
	private void rotateForIndex(int i, Camera camera) {
		switch (i) {
		case 0:
			camera.rotate(new Vector4f(0,0,1, -180));
			break;
		case 1:
			camera.rotate(new Vector4f(0,1,0, -180));
			break;
		case 2:
			camera.rotate(new Vector4f(0,1,0, 90));
			camera.rotate(new Vector4f(1,0,0, 90));
			break;
		case 3:
			camera.rotate(new Vector4f(1,0,0, -180));
			break;
		case 4:
			camera.rotate(new Vector4f(1,0,0, 90));
			break;
		case 5:
			camera.rotate(new Vector4f(0,1,0, -180));
			break;
		default:
			break;
		}

		camera.updateShadow();
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

	private FPSCounter fpsCounter = new FPSCounter();
	private void setLastFrameTime() {
		Display.setTitle("FPS: " + (int)(fpsCounter.getFPS()));
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

	public void drawLines(Program program) {

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
	

	ForkJoinPool fjpool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()*2);

	private List<Vector3f> linePoints = new ArrayList<>();
	private FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);
	private RenderTarget[] cubeMapRenderTargets;
	private Camera cubeMapCam = new Camera(this, Util.createPerpective(90f, 1, 0.1f, 500f), 0.1f, 500f);

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
	public LightFactory getLightFactory() {
		return lightFactory;
	}

	public void setLightFactory(LightFactory lightFactory) {
		this.lightFactory = lightFactory;
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

	@Override
	public ProgramFactory getProgramFactory() {
		return programFactory;
	}

	@Override
	public void drawDebug(Camera camera, DynamicsWorld dynamicsWorld, Octree octree, List<IEntity> entities,
			Spotlight light) {

		gBuffer.drawDebug(camera, dynamicsWorld, octree, entities, light, pointLights, cubeMap);
		GL11.glViewport(0, 0, Renderer.WIDTH, Renderer.HEIGHT);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
	    
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		drawToQuad(gBuffer.getColorReflectivenessImage(), fullscreenBuffer);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}
}
