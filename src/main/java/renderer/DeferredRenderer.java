package renderer;

import com.bulletphysics.dynamics.DynamicsWorld;
import component.ModelComponent;
import config.Config;
import engine.TimeStepThread;
import engine.World;
import engine.model.*;
import event.StateChangedEvent;
import octree.Octree;
import org.apache.commons.io.FileUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.*;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.KHRDebugCallback.Handler;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.command.Command;
import renderer.command.RenderProbeCommandQueue;
import renderer.light.DirectionalLight;
import renderer.light.LightFactory;
import renderer.light.PointLight;
import renderer.material.Material;
import renderer.material.Material.MAP;
import renderer.material.MaterialFactory;
import renderer.rendertarget.RenderTarget;
import scene.EnvironmentProbe;
import scene.EnvironmentProbe.Update;
import scene.EnvironmentProbeFactory;
import shader.Program;
import shader.ProgramFactory;
import shader.StorageBuffer;
import texture.CubeMap;
import texture.TextureFactory;
import util.stopwatch.GPUProfiler;
import util.stopwatch.GPUTaskProfile;
import util.stopwatch.OpenGLStopWatch;
import util.stopwatch.StopWatch;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static log.ConsoleLogger.getLogger;

public class DeferredRenderer implements Renderer {
	private static boolean IGNORE_GL_ERRORS = !(java.lang.management.ManagementFactory.getRuntimeMXBean().
		    getInputArguments().toString().indexOf("-agentlib:jdwp") > 0);
	private int frameCount = 0;

	private static Logger LOGGER = getLogger();

	private volatile boolean initialized = false;

	private BlockingQueue<Command> workQueue = new LinkedBlockingQueue<Command>();
	private Map<Command<? extends Result<?>>, SynchronousQueue<Result<? extends Object>>> commandQueueMap = new ConcurrentHashMap<>();
	
	private RenderProbeCommandQueue renderProbeCommandQueue = new RenderProbeCommandQueue();

	public static EnumSet<DataChannels> RENDERTOQUAD = EnumSet.of(
			DataChannels.POSITION3,
			DataChannels.TEXCOORD);
	
	public int testTexture = -1;
	private OpenGLStopWatch glWatch;

	private FloatBuffer matrix44Buffer = BufferUtils.createFloatBuffer(16);
	private static Program renderToQuadProgram;
	private static Program blurProgram;
	private static Program bilateralBlurProgram;
	private Program lastUsedProgram = null;
	
	private VertexBuffer fullscreenBuffer;
	private VertexBuffer debugBuffer;

	private static float MINLIGHTRADIUS = 64.5f;
	private static float LIGHTRADIUSSCALE = 15f;
	private static int MAXLIGHTS = 3;
	
	private Entity sphere;

	public CubeMap cubeMap;
	private EnvironmentSampler environmentSampler;

	private OBJLoader objLoader;
	private ProgramFactory programFactory;
	private EntityFactory entityFactory;
	private LightFactory lightFactory;
	private EnvironmentProbeFactory environmentProbeFactory;
	private MaterialFactory materialFactory;
	private TextureFactory textureFactory;

	private Model sphereModel;

	private FloatBuffer identityMatrix44Buffer;

	private GBuffer gBuffer;
	private RenderTarget fullScreenTarget;
	private RenderTarget halfScreenTarget;

	private Program cubeMapDiffuseProgram;
	private int maxTextureUnits;
	private World world;
	private String currentState = "";

	public DeferredRenderer(World world, boolean headless) {
		DeferredRenderer renderer = this;

		new TimeStepThread("Renderer"){
			public void update(float seconds) {
				if(!initialized) {
					setCurrentState("INITIALIZING");
					setupOpenGL(headless);
					renderer.world = world;
					world.setRenderer(renderer);
					objLoader = new OBJLoader(renderer);
					textureFactory = new TextureFactory(renderer);
					DeferredRenderer.exitOnGLError("After TextureFactory");
					programFactory = new ProgramFactory(world);
					setupShaders();
					setUpGBuffer();
					fullScreenTarget = new RenderTarget(Config.WIDTH, Config.HEIGHT, GL11.GL_RGBA8);
					materialFactory = new MaterialFactory(renderer);
					entityFactory = new EntityFactory(world);
					lightFactory = new LightFactory(world);
					environmentProbeFactory = new EnvironmentProbeFactory(world);
//		environmentProbeFactory.getProbe(new Vector3f(-10,30,-1), new Vector3f(490, 250, 220), Update.DYNAMIC);
//		environmentProbeFactory.getProbe(new Vector3f(160,10,0), 100, Update.DYNAMIC);
					gBuffer.init(renderer);

					sphereModel = null;
					try {
						sphereModel = objLoader.loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sphere.obj")).get(0);
						sphereModel.setMaterial(getMaterialFactory().getDefaultMaterial());
					} catch (IOException e) {
						e.printStackTrace();
					} catch (Exception e) {
						e.printStackTrace();
					}
//		_addPointLights();
//		_addPointLightsGrid();
//		_addEnvironmentPorbesGrid();
				}

				initialized = true;

				//TODO: throws illegalstateexception
				while (Display.isCreated() && !Display.isCloseRequested()) {
					if(world.isInitialized()) {
						setCurrentState("BEFORE DRAW");
						draw();
						long start = System.currentTimeMillis();
						Display.update();
//						System.out.println("Display update " + (System.currentTimeMillis() - start));
						setCurrentState("AFTER DRAW");
					}
					setCurrentState("BEFORE UPDATE");
					renderer.update(world, seconds);
					setCurrentState("AFTER UPDATE");
				}

			}
		}.start();

	}

	private void _addPointLights() {
		Random randomGenerator = new Random();
		for (int i = 0; i < MAXLIGHTS; i++) {
			Material white = materialFactory.getMaterial(new HashMap<MAP,String>(){{
				put(MAP.DIFFUSE,"assets/textures/default.dds");
			}});
			//Vector4f color = new Vector4f(randomGenerator.nextFloat(),randomGenerator.nextFloat(),randomGenerator.nextFloat(),1);
			Vector4f color = new Vector4f(1,1,1,1);
			Vector3f position = new Vector3f(i*randomGenerator.nextFloat()*2,randomGenerator.nextFloat(),i*randomGenerator.nextFloat());
			float range = MINLIGHTRADIUS + LIGHTRADIUSSCALE* randomGenerator.nextFloat();
			PointLight pointLight = lightFactory.getPointLight(position, sphereModel, color, range);
		}
	}

	private void _addEnvironmentPorbesGrid() {
		int size = 60;
		int count = 64;
		count = (int) Math.sqrt(count);
		int maxXY = (int) (size*count*0.5);
		int minXY = -maxXY;
		int gap = -20;
		for (int i = 0; i < count; i++) {
			for (int z = 0; z < count; z++) {
				Vector3f position = new Vector3f(minXY + i*size+gap, 0, minXY + z*size+gap);
				environmentProbeFactory.getProbe(position, size, Update.DYNAMIC, 1);
			}
		}
	}
	private void _addPointLightsGrid() {
		int size = 140;
		int count = 40;
		count = (int) Math.sqrt(count);
		int maxXY = (int) (size*count*0.5);
		int minXY = -maxXY;
		int gap = 5;
		for (int i = 0; i < count; i++) {
			for (int z = 0; z < count; z++) {
				Vector4f color = new Vector4f(0.3f,0.3f,0.3f,size);
				Vector3f position = new Vector3f(minXY + i*2*size+gap, size/3, minXY + z*2*size+gap);
				PointLight pointLight = lightFactory.getPointLight(position, sphereModel, color, size);
			}
		}

		lightFactory.getTubeLight(400, 70);
//		areaLights.add(lightFactory.getAreaLight(50,50,100));
//		int arealightSize = 2048/2/2/2/2/2/2/2;
//		for (int i = 0; i < rsmSize*rsmSize; i++) {
//			areaLights.add(lightFactory.getAreaLight(10,10,20));
//		}
	}

	private void setUpGBuffer() {
		DeferredRenderer.exitOnGLError("Before setupGBuffer");

		firstPassProgram = programFactory.getProgram("first_pass_vertex.glsl", "first_pass_fragment.glsl");
		secondPassPointProgram = programFactory.getProgram("second_pass_point_vertex.glsl", "second_pass_point_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
		secondPassTubeProgram = programFactory.getProgram("second_pass_point_vertex.glsl", "second_pass_tube_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
		secondPassAreaProgram = programFactory.getProgram("second_pass_area_vertex.glsl", "second_pass_area_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
		secondPassDirectionalProgram = programFactory.getProgram("second_pass_directional_vertex.glsl", "second_pass_directional_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
		instantRadiosityProgram = programFactory.getProgram("second_pass_area_vertex.glsl", "second_pass_instant_radiosity_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);

		combineProgram = programFactory.getProgram("combine_pass_vertex.glsl", "combine_pass_fragment.glsl", RENDERTOQUAD, false);
		postProcessProgram = programFactory.getProgram("passthrough_vertex.glsl", "postprocess_fragment.glsl", RENDERTOQUAD, false);

		gBuffer = new GBuffer(world, this, firstPassProgram, secondPassDirectionalProgram, secondPassPointProgram, secondPassTubeProgram, secondPassAreaProgram, combineProgram, postProcessProgram, instantRadiosityProgram);

//		environmentSampler = new EnvironmentSampler(this, new Vector3f(0,-200,0), 128, 128);
		
		setMaxTextureUnits(GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS));
		GL11.glEnable(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS);
		
		DeferredRenderer.exitOnGLError("setupGBuffer");
	}
	
	protected void finalize() throws Throwable {
		destroy();
	}
	

	private void setupOpenGL(boolean headless) {
		try {

			if(headless) {
				Canvas canvas = new Canvas();
				Display.setParent(canvas);
				frame = new JFrame("hpengine");
				frame.setSize(new Dimension(Config.WIDTH, Config.HEIGHT));
				JLayeredPane layeredPane = new JLayeredPane();
				layeredPane.setPreferredSize(new Dimension(Config.WIDTH, Config.HEIGHT));
				layeredPane.add(canvas, 0);
				JPanel overlayPanel = new JPanel();
				overlayPanel.setOpaque(true);
				overlayPanel.add(new JButton("asdasdasd"));
	//			layeredPane.add(overlayPanel, 1);
				//frame.add(layeredPane);
	//			frame.setLayout(new BorderLayout());
				frame.getContentPane().add(new JButton("adasd"), BorderLayout.PAGE_START);
				frame.getContentPane().add(canvas, BorderLayout.CENTER);
				frame.pack();
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				frame.setVisible(true);

				frame.setVisible(false);
			}
			
			PixelFormat pixelFormat = new PixelFormat();
			ContextAttribs contextAtrributes = new ContextAttribs(4, 3)
//				.withProfileCompatibility(true)
//				.withForwardCompatible(true)
				.withProfileCore(true)
//				.withDebug(true)
				;
//				.withProfileCore(true);
			
			Display.setDisplayMode(new DisplayMode(Config.WIDTH, Config.HEIGHT));
			Display.setVSyncEnabled(false);
			Display.setTitle("DeferredRenderer");
			Display.create(pixelFormat, contextAtrributes);
			Display.setResizable(false);
			Display.setVSyncEnabled(World.VSYNC_ENABLED);
			Handler handler = new Handler() {
				@Override
				public void handleMessage(int source, int type, int id, int severity, String message) {
					if(severity == KHRDebug.GL_DEBUG_SEVERITY_HIGH) {
						Logger.getGlobal().severe(message);
					}
				}
			};
			GL43.glDebugMessageCallback(new KHRDebugCallback(handler));
			
			GL11.glViewport(0, 0, Config.WIDTH, Config.HEIGHT);
		} catch (LWJGLException e) {
			e.printStackTrace();
			System.exit(-1);
		}

//		GL11.glClearColor(0.4f, 0.6f, 0.9f, 0f);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_CULL_FACE);
//		GL11.glDisable(GL11.GL_CULL_FACE);
		
		// Map the internal OpenGL coordinate system to the entire screen
		GL11.glViewport(0, 0, Config.WIDTH, Config.HEIGHT);
		
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
		try {
			cubeMap = textureFactory.getCubeMap("hp/assets/textures/skybox.png");
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			textureFactory.generateMipMapsCubeMap(cubeMap.getTextureID());
//			cubeMap = new DynamicCubeMap(1024, 1024);
//			DeferredRenderer.exitOnGLError("setup cubemap");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		renderToQuadProgram = programFactory.getProgram("passthrough_vertex.glsl", "simpletexture_fragment.glsl", RENDERTOQUAD, false);
		blurProgram = programFactory.getProgram("passthrough_vertex.glsl", "blur_fragment.glsl", RENDERTOQUAD, false);
		bilateralBlurProgram = programFactory.getProgram("passthrough_vertex.glsl", "blur_bilateral_fragment.glsl", RENDERTOQUAD, false);

		DeferredRenderer.exitOnGLError("setupShaders");
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
	
	@Override
	public void init(Octree octree) {
		addCommand(new Command() {
			@Override
			public Result execute(World world) {

				environmentProbeFactory.drawInitial(octree);

				final int initialDrawCount = 10;
				for(int i = 0; i < initialDrawCount; i++) {
					for (EnvironmentProbe probe : environmentProbeFactory.getProbes()) {
						addRenderProbeCommand(probe, true);
					}
				}

				return new Result(new Object());
			}
		});
	}

	public void update(World world, float seconds) {
		try {
			executeCommands(world);
		} catch (Exception e) {
			e.printStackTrace();
		}
//		FileMonitor.getInstance().checkAndNotify();
		updateLights(seconds);
	}


	// I need this to force probe redrawing after engine startup....TODO: Find better solution
	int counter = 0;

	private void draw() {

		StopWatch.getInstance().start("Draw");
		setLastFrameTime();
		if (World.DRAWLINES_ENABLED) {
			drawDebug(world.getActiveCameraEntity(), world.getPhysicsFactory().getDynamicsWorld(), world.getScene().getOctree(), world.getScene().getEntities());
		} else {
//			fireRenderProbeCommands();
			draw(world.getActiveCameraEntity(), world, world.getScene().getEntities());
		}

		if(counter < 20) {
			getLightFactory().getDirectionalLight().rotate(new Vector4f(0, 1, 0, 0.001f));
			World.CONTINUOUS_DRAW_PROBES = true;
			counter++;
		} else if(counter == 20) {
			World.CONTINUOUS_DRAW_PROBES = false;
			counter++;
		}

		fpsCounter.update();
		StopWatch.getInstance().stopAndPrintMS();

//		Renderer.exitOnGLError("draw in render");
	}

	public void draw(Entity camera, World world, List<Entity> entities) {
		GPUProfiler.startFrame();
		draw(null, world.getScene().getOctree(), camera, entities);
	    GPUTaskProfile tp;
	    while((tp = GPUProfiler.getFrameResults()) != null){
	        
	        tp.dump(); //Dumps the frame to System.out.
	    }
		GPUProfiler.endFrame();
	    
	    frameCount++;
	}
	
	private void draw(RenderTarget target, Octree octree, Entity camera, List<Entity> entities) {
		DirectionalLight light = getLightFactory().getDirectionalLight();
		GPUProfiler.start("First pass");
		gBuffer.drawFirstPass(camera, octree, lightFactory.getPointLights(), lightFactory.getTubeLights(), lightFactory.getAreaLights());
		GPUProfiler.end();

		if (!World.DEBUGDRAW_PROBES) {
			environmentProbeFactory.drawAlternating(octree, camera, light, frameCount);
			executeRenderProbeCommands(octree, camera, light);
			GPUProfiler.start("Shadowmap pass");
			if(light.hasMoved() || !octree.getEntities().parallelStream().filter(e -> { return e.hasMoved(); }).collect(Collectors.toList()).isEmpty())
			{
				GPUProfiler.start("Directional shadowmap");
				light.drawShadowMap(octree);
				GPUProfiler.end();
			}
			lightFactory.renderAreaLightShadowMaps(octree);
			//		doInstantRadiosity(light);
			GPUProfiler.end();
			GPUProfiler.start("Second pass");
			gBuffer.drawSecondPass(camera, light, lightFactory.getPointLights(), lightFactory.getTubeLights(), lightFactory.getAreaLights(), cubeMap);
			GPUProfiler.end();
			GL11.glViewport(0, 0, Config.WIDTH, Config.HEIGHT);
			GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GPUProfiler.start("Combine pass");
			gBuffer.combinePass(target, light, camera);
			GPUProfiler.end();
		} else {
			GL11.glViewport(0, 0, Config.WIDTH, Config.HEIGHT);
			GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
		    
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
			drawToQuad(gBuffer.getColorReflectivenessMap(), fullscreenBuffer); // the first color attachment
		}

		if (World.DEBUGFRAME_ENABLED) {
//			drawToQuad(lightFactory.getDepthMapForAreaLight(getLightFactory().getAreaLights().get(0)), debugBuffer);
			drawToQuad(gBuffer.getNormalMap(), debugBuffer);
//			drawToQuad(light.getShadowMapId(), debugBuffer);
		}
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}

	private void drawToQuad(int texture, VertexBuffer buffer) {
		drawToQuad(texture, buffer, renderToQuadProgram);
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer, Program program) {
		program.use();

		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, gBuffer.getNormalMap());

		buffer.draw();
	}

	@Override
	public void blur2DTexture(int sourceTextureId, int mipmap, int width, int height, int internalFormat, boolean upscaleToFullscreen, int blurTimes) {
		GPUProfiler.start("BLURRRRRRR");
		int copyTextureId = GL11.glGenTextures();
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTextureId);
		
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		
		GL43.glCopyImageSubData(sourceTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				width, height, 1);
		
		float scaleForShaderX = (float) (Config.WIDTH / width);
		float scaleForShaderY = (float) (Config.HEIGHT / height);
		// TODO: Reset texture sizes after upscaling!!!
		if(upscaleToFullscreen) {
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTextureId);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, Config.WIDTH, Config.HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
			scaleForShaderX = 1;
			scaleForShaderY = 1;
		}

		fullScreenTarget.use(false);
		fullScreenTarget.setTargetTexture(sourceTextureId, 0);
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTextureId);

		blurProgram.use();
		blurProgram.setUniform("mipmap", mipmap);
		blurProgram.setUniform("scaleX", scaleForShaderX);
		blurProgram.setUniform("scaleY", scaleForShaderY);
		fullscreenBuffer.draw();
		fullScreenTarget.unuse();
		GL11.glDeleteTextures(copyTextureId);
		GPUProfiler.end();
	}
	@Override
	public void blur2DTextureBilateral(int sourceTextureId, int edgeTexture, int width, int height, int internalFormat, boolean upscaleToFullscreen, int blurTimes) {
		int copyTextureId = GL11.glGenTextures();
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTextureId);
		
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		
		GL43.glCopyImageSubData(sourceTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				width, height, 1);
		
		float scaleForShaderX = (float) (Config.WIDTH / width);
		float scaleForShaderY = (float) (Config.HEIGHT / height);
		if(upscaleToFullscreen) {
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTextureId);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, Config.WIDTH, Config.HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
			scaleForShaderX = 1;
			scaleForShaderY = 1;
		}

		fullScreenTarget.use(false);
		fullScreenTarget.setTargetTexture(sourceTextureId, 0);
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTextureId);

		bilateralBlurProgram.use();
		bilateralBlurProgram.setUniform("scaleX", scaleForShaderX);
		bilateralBlurProgram.setUniform("scaleY", scaleForShaderY);
		fullscreenBuffer.draw();
		fullScreenTarget.unuse();
		GL11.glDeleteTextures(copyTextureId);
	}
	
	public void destroy() {
		System.out.println("Finalize renderer");
		destroyOpenGL();
		if(frame != null) {
			frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
		}
	}

	private void destroyOpenGL() {
		// Delete the shaders
//		materialProgram.delete();
//
//		DeferredRenderer.exitOnGLError("destroyOpenGL");
		addCommand(new Command<Result<Object>>() {
			@Override
			public Result<Object> execute(World world) {
				if(!Display.isCloseRequested()) {
					Display.destroy();
				}
				return new Result<Object>(true);
			}
		});
	}

	private static long lastFrameTime = 0l;

	private FPSCounter fpsCounter = new FPSCounter();
	private void setLastFrameTime() {
		lastFrameTime = getTime();
		Display.setTitle("FPS: " + (int)(fpsCounter.getFPS()) + " | " + fpsCounter.getMsPerFrame() + " ms" + " Renderstate: " + getCurrentState());
	}
	private long getTime() {
		return System.currentTimeMillis();
	}
	public double getDeltainMS() {
		long currentTime = getTime();
		double delta = (double) currentTime - (double) lastFrameTime;
		return delta;
	}
	@Override
	public double getDeltaInS() {
		return (getDeltainMS() / 1000d);
	}

	public static void exitOnGLError(String errorMessage) {
		if(IGNORE_GL_ERRORS) { return; }
		int errorValue = GL11.glGetError();
		
		if (errorValue != GL11.GL_NO_ERROR) {
			String errorString = GLU.gluErrorString(errorValue);
			new Exception().printStackTrace(System.err);
			System.err.println("ERROR - " + errorMessage + ": " + errorString);
			
			if (Display.isCreated()) Display.destroy();
			System.exit(-1);
		}
	}

	@Override
	public boolean isInitialized() {
		return initialized;
	}

	@Override
	public float getElapsedSeconds() {
		return (float)getDeltaInS();
	}

	public void drawLines(Program program) {

//		program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);
//		program.setUniform("materialDiffuseColor", new Vector3f(1,0,0));
		float[] points = new float[linePoints.size() * 3];
		for (int i = 0; i < linePoints.size(); i++) {
			Vector3f point = linePoints.get(i);
			points[3*i + 0] = point.x;
			points[3*i + 1] = point.y;
			points[3*i + 2] = point.z;
		}
		VertexBuffer buffer = new VertexBuffer(points, EnumSet.of(DataChannels.POSITION3)).upload();
		buffer.drawDebug();
		buffer.delete();
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
	private int rsmSize = 2048/2/2/2/2/2/2/2;
	private Program firstPassProgram;
	private Program secondPassPointProgram;
	private Program secondPassTubeProgram;
	private Program secondPassAreaProgram;
	private Program secondPassDirectionalProgram;
	private Program instantRadiosityProgram;
	private Program combineProgram;
	private Program postProcessProgram;
	private JFrame frame;

	private void updateLights(float seconds) {
//		for (PointLight light : pointLights) {
//			double sinusX = 10f*Math.sin(100000000f/System.currentTimeMillis());
//			double sinusY = 1f*Math.sin(100000000f/System.currentTimeMillis());
//			light.move(new Vector3f((float)sinusX,(float)sinusY, 0f));
//			light.update();
//		}
		 RecursiveAction task = new RecursiveUpdate(lightFactory.getPointLights(), 0, lightFactory.getPointLights().size(), seconds);
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
//					double x =  Math.sin(System.currentTimeMillis() / 1000);
//					double z =  Math.cos(System.currentTimeMillis() / 1000);
//					lights.get(i).move(new Vector3f((float)x , 0, (float)z));
//					lights.get(i).update(1);
					lights.get(i).update(seconds);
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


	@Override
	public <OBJECT_TYPE, RESULT_TYPE extends Result<OBJECT_TYPE>> SynchronousQueue<RESULT_TYPE> addCommand(Command<RESULT_TYPE> command) {
		SynchronousQueue<RESULT_TYPE> queue = new SynchronousQueue<>();
	    commandQueueMap.put(command, (SynchronousQueue<Result<? extends Object>>) queue);
	    workQueue.offer(command);
	    return queue;
	}

	private void executeCommands(World world) throws Exception {
        Command command = workQueue.poll();
        while(command != null) {
			setCurrentState("BEFORE EXECUTION " + command.getClass().getSimpleName());
        	Result result = command.execute(world);
			setCurrentState("AFTER EXECUTION " + command.getClass().getSimpleName());
            SynchronousQueue<Result<?>> queue = commandQueueMap.get(command);
            try {
				queue.offer(result);

			} catch (NullPointerException e) {
				Logger.getGlobal().info("Got null for command " + command.toString());
			}
			command = workQueue.poll();
        }
	}
	
	private void executeRenderProbeCommands(Octree octree, Entity camera, DirectionalLight light) {
//		environmentProbeFactory.prepareProbeRendering();
		int counter = 0;
		
		renderProbeCommandQueue.takeNearest(camera).ifPresent(command -> {
			command.getProbe().draw(octree, command.isUrgent());
		});
		counter++;
		
		while(counter < RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL) {
			renderProbeCommandQueue.take().ifPresent(command -> {
				command.getProbe().draw(octree, command.isUrgent());
			});
			counter++;
		}
		counter = 0;
	}

	@Override
	public void addRenderProbeCommand(EnvironmentProbe probe) {
		addRenderProbeCommand(probe, false);
	}
	@Override
	public void addRenderProbeCommand(EnvironmentProbe probe, boolean urgent) {
		renderProbeCommandQueue.addProbeRenderCommand(probe, urgent);
	}

	@Override
	public ProgramFactory getProgramFactory() {
		return programFactory;
	}

	@Override
	public EnvironmentProbeFactory getEnvironmentProbeFactory() {
		return environmentProbeFactory;
	}
	@Override
	public void drawDebug(Entity camera, DynamicsWorld dynamicsWorld, Octree octree, List<Entity> entities) {

		gBuffer.drawDebug(camera, dynamicsWorld, octree, entities, lightFactory.getPointLights(), lightFactory.getTubeLights(), lightFactory.getAreaLights(), cubeMap);
		GL11.glViewport(0, 0, Config.WIDTH, Config.HEIGHT);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
	    
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		drawToQuad(gBuffer.getPositionMap(), fullscreenBuffer); // the first color attachment
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}

	@Override
	public int getMaxTextureUnits() {
		return maxTextureUnits;
	}

	private void setMaxTextureUnits(int maxTextureUnits) {
		this.maxTextureUnits = maxTextureUnits;
	}

	public RenderTarget getHalfScreenTarget() {
		return halfScreenTarget;
	}
	
	public float getCurrentFPS() {
		return fpsCounter.getFPS();
	}

	public int getFrameCount() {
		return frameCount;
	}

	@Override
	public Program getFirstPassProgram() {
		return firstPassProgram;
	}
	@Override
	public Program getSecondPassPointProgram() {
		return secondPassPointProgram;
	}
	@Override
	public Program getSecondPassTubeProgram() {
		return secondPassTubeProgram;
	}
	@Override
	public Program getSecondPassDirectionalProgram() {
		return secondPassDirectionalProgram;
	}
	@Override
	public Program getSecondPassAreaLightProgram() {
		return secondPassAreaProgram;
	}
	@Override
	public Program getCombineProgram() {
		return combineProgram;
	}
	@Override
	public Program getPostProcessProgram() {
		return postProcessProgram;
	}

	@Override
	public StorageBuffer getStorageBuffer() {
		return gBuffer.getStorageBuffer();
	}

	@Override
	public String getCurrentState() {
		return currentState;
	}
	private void setCurrentState(String newState) {
		currentState = newState;
		World.getEventBus().post(new StateChangedEvent(newState));
	}
}
