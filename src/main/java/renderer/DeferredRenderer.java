package renderer;

import camera.Camera;
import config.Config;
import engine.OpenGLTimeStepThread;
import engine.TimeStepThread;
import engine.World;
import engine.model.*;
import event.StateChangedEvent;
import octree.Octree;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.command.Command;
import renderer.command.RenderProbeCommandQueue;
import renderer.command.Result;
import renderer.drawstrategy.DebugDrawStrategy;
import renderer.drawstrategy.DrawStrategy;
import renderer.drawstrategy.GBuffer;
import renderer.drawstrategy.SimpleDrawStrategy;
import renderer.fps.FPSCounter;
import renderer.light.DirectionalLight;
import renderer.light.LightFactory;
import renderer.light.PointLight;
import renderer.material.MaterialFactory;
import renderer.rendertarget.ColorAttachmentDefinition;
import renderer.rendertarget.RenderTarget;
import renderer.rendertarget.RenderTargetBuilder;
import scene.EnvironmentProbe;
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
import javax.vecmath.Vector2f;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static log.ConsoleLogger.getLogger;

public class DeferredRenderer implements Renderer {
	private static boolean IGNORE_GL_ERRORS = !(java.lang.management.ManagementFactory.getRuntimeMXBean().
		    getInputArguments().toString().indexOf("-agentlib:jdwp") > 0);

	private final boolean headless;
	private int frameCount = 0;

	private static Logger LOGGER = getLogger();

	private volatile boolean initialized = false;

	private BlockingQueue<Command> workQueue = new LinkedBlockingQueue<Command>();
	private Map<Command<? extends Result<?>>, SynchronousQueue<Result<? extends Object>>> commandQueueMap = new ConcurrentHashMap<>();
	
	private RenderProbeCommandQueue renderProbeCommandQueue = new RenderProbeCommandQueue();

	public static EnumSet<DataChannels> RENDERTOQUAD = EnumSet.of(
			DataChannels.POSITION3,
			DataChannels.TEXCOORD);
	
	private OpenGLStopWatch glWatch;

	private FloatBuffer matrix44Buffer = BufferUtils.createFloatBuffer(16);
	private static Program renderToQuadProgram;
	private static Program blurProgram;
	private static Program bilateralBlurProgram;
	private Program lastUsedProgram = null;

	private VertexBuffer fullscreenBuffer;
	private VertexBuffer debugBuffer;
	private ArrayList<VertexBuffer> sixDebugBuffers;

	public CubeMap cubeMap;

	private OBJLoader objLoader;
	private ProgramFactory programFactory;
	private LightFactory lightFactory;
	private EnvironmentProbeFactory environmentProbeFactory;
	private MaterialFactory materialFactory;
	private TextureFactory textureFactory;

	private Model sphereModel;

	private FloatBuffer identityMatrix44Buffer;

	private GBuffer gBuffer;
	private SimpleDrawStrategy simpleDrawStrategy;
	private DebugDrawStrategy debugDrawStrategy;
	private DrawStrategy currentDrawStrategy;

	private RenderTarget fullScreenTarget;
	private RenderTarget halfScreenTarget;

	private int maxTextureUnits;
	private World world;
	private String currentState = "";

	private ExecutorService renderThread = Executors.newSingleThreadExecutor();
	TimeStepThread drawThread;
	private OpenGLContext openGLContext;

	public DeferredRenderer(boolean headless) {
		this.headless = headless;
	}

	@Override
	public void init(World world) {
		Renderer.super.init(world);
		DeferredRenderer renderer = this;

		drawThread = new TimeStepThread("Renderer") {
			public void cleanUp() {
				renderer.destroy();
			}
			public void update(float seconds) {
				Thread.currentThread().setName("Renderer");
				if (!initialized) {
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
					renderer.simpleDrawStrategy = new SimpleDrawStrategy(renderer);
					renderer.debugDrawStrategy = new DebugDrawStrategy(renderer);
					renderer.currentDrawStrategy = simpleDrawStrategy;

					fullScreenTarget = new RenderTargetBuilder().setWidth(Config.WIDTH)
												.setHeight(Config.HEIGHT)
												.add(new ColorAttachmentDefinition().setInternalFormat(GL11.GL_RGBA8))
												.build();
					materialFactory = new MaterialFactory(renderer);
					lightFactory = new LightFactory(world);
					environmentProbeFactory = new EnvironmentProbeFactory(world);
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
				}

				initialized = true;

				//TODO: throws illegalstateexception
				if (Display.isCreated() && !Display.isCloseRequested()) {
					if (world.isInitialized()) {
						if(world.getScene().isInitialized())
						{
							setCurrentState("BEFORE DRAW");
							draw(world);
						}
						Display.update();
						setCurrentState("AFTER DRAW");

						setCurrentState("BEFORE UPDATE");
						renderer.update(world, seconds);
						setCurrentState("AFTER UPDATE");
					}
				}
			}
		};
		renderThread.submit(drawThread);
	}

	private void setupOpenGL(boolean headless) {
		try {

			openGLContext = new OpenGLContext(headless);

			if(headless)
			{
				Canvas canvas = new Canvas();
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
				frame.getContentPane().add(new JButton("xxx"), BorderLayout.PAGE_END);
				frame.getContentPane().add(canvas, BorderLayout.CENTER);
				frame.pack();
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				frame.setVisible(true);
				frame.setVisible(false);
//				openGLContext.attach(canvas);
			}

		} catch (LWJGLException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		initIdentityMatrixBuffer();

		fullscreenBuffer = new QuadVertexBuffer( true).upload();
		debugBuffer = new QuadVertexBuffer( false).upload();
		sixDebugBuffers = new ArrayList<VertexBuffer>() {{
			float height = -2f/3f;
			float width = 2f;
			float widthDiv = width/6f;
			for (int i = 0; i < 6; i++) {
				add(new QuadVertexBuffer(new Vector2f(-1f + i * widthDiv, -1f), new Vector2f(-1 + (i + 1) * widthDiv, height)).upload());
			}
		}};
		
		glWatch = new OpenGLStopWatch();

		DeferredRenderer.exitOnGLError("setupOpenGL");
//		CLUtil.initialize();
	}
	private void setUpGBuffer() {
		DeferredRenderer.exitOnGLError("Before setupGBuffer");

		gBuffer = new GBuffer(world, this);

		setMaxTextureUnits(GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS));
		GL11.glEnable(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS);

		DeferredRenderer.exitOnGLError("setupGBuffer");
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
		updateLights(seconds);
	}


	// I need this to force probe redrawing after engine startup....TODO: Find better solution
	int counter = 0;

	public void draw(World world) {
		GPUProfiler.startFrame();
		setLastFrameTime();

		if (World.DRAWLINES_ENABLED) {
			debugDrawStrategy.draw(world);
		} else {
			simpleDrawStrategy.draw(world);
		}

		if (World.DEBUGFRAME_ENABLED) {
//			drawToQuad(lightFactory.getDirectionalLight().getShadowMapId(), debugBuffer);
			drawToQuad(gBuffer.getNormalMap(), debugBuffer);
//			for(int i = 0; i < 6; i++) {
//				drawToQuad(environmentProbeFactory.getProbes().get(0).getSampler().getCubeMapFaceViews()[1][i], sixDebugBuffers.get(i));
//			}
//			drawToQuad(light.getShadowMapId(), debugBuffer);
		}

		if(counter < 20) {
			world.getScene().getDirectionalLight().rotate(new Vector4f(0, 1, 0, 0.001f));
			World.CONTINUOUS_DRAW_PROBES = true;
			counter++;
		} else if(counter == 20) {
			World.CONTINUOUS_DRAW_PROBES = false;
			counter++;
		}

		fpsCounter.update();
		GPUProfiler.endFrame();

		dumpTimings();

		frameCount++;

//		Renderer.exitOnGLError("draw in render");
	}

	private void dumpTimings() {
		GPUTaskProfile tp;
		while((tp = GPUProfiler.getFrameResults()) != null){
			tp.dump(); //Dumps the frame to System.out.
		}
	}

	@Override
	public void drawToQuad(int texture) {
		drawToQuad(texture, fullscreenBuffer, renderToQuadProgram);
	}

	public void drawToQuad(int texture, VertexBuffer buffer) {
		drawToQuad(texture, buffer, renderToQuadProgram);
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer, Program program) {
		program.use();
		GL11.glDisable(GL11.GL_DEPTH_TEST);

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
		System.exit(0);
	}

	private void destroyOpenGL() {
		drawThread.stopRequested = true;
		Display.destroy();
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

	@Override
	public void executeRenderProbeCommands() {
		int counter = 0;
		
		renderProbeCommandQueue.takeNearest(world.getActiveCamera()).ifPresent(command -> {
			command.getProbe().draw(world, command.isUrgent());
		});
		counter++;
		
		while(counter < RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL) {
			renderProbeCommandQueue.take().ifPresent(command -> {
				command.getProbe().draw(world, command.isUrgent());
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
	public StorageBuffer getStorageBuffer() {
		return gBuffer.getStorageBuffer();
	}

	@Override
	public String getCurrentState() {
		return currentState;
	}

	@Override
	public void endFrame() {

		DirectionalLight light = world.getScene().getDirectionalLight();
		light.setHasMoved(false);
		for (Entity entity : getLightFactory().getPointLights()) {
			entity.setHasMoved(false);
		}
		for (Entity entity : getLightFactory().getAreaLights()) {
			entity.setHasMoved(false);
		}
	}

	private void setCurrentState(String newState) {
		currentState = newState;
		World.getEventBus().post(new StateChangedEvent(newState));
	}

	protected void finalize() throws Throwable {
		destroy();
	}

	@Override
	public void setWorld(World world) {
		this.world = world;
	}

	@Override
	public World getWorld() {
		return world;
	}

	@Override
	public GBuffer getGBuffer() {
		return gBuffer;
	}

	@Override
	public VertexBuffer getFullscreenBuffer() {
		return fullscreenBuffer;
	}
}
