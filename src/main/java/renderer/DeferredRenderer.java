package renderer;

import config.Config;
import engine.AppContext;
import engine.TimeStepThread;
import engine.Transform;
import engine.model.*;
import event.StateChangedEvent;
import octree.Octree;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.command.Command;
import renderer.command.RenderProbeCommandQueue;
import renderer.command.Result;
import renderer.constants.GlCap;
import renderer.constants.GlTextureTarget;
import renderer.drawstrategy.DebugDrawStrategy;
import renderer.drawstrategy.DrawStrategy;
import renderer.drawstrategy.GBuffer;
import renderer.drawstrategy.SimpleDrawStrategy;
import renderer.light.DirectionalLight;
import renderer.light.LightFactory;
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

import javax.swing.*;
import javax.vecmath.Vector2f;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
	private static Program linesProgram;
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
	private AppContext appContext;
	private String currentState = "";

	private ExecutorService renderThread = Executors.newSingleThreadExecutor();

	private TimeStepThread drawThread;
	private OpenGLContext openGLContext;

	public DeferredRenderer(boolean headless) {
		this.headless = headless;
	}

	@Override
	public void init(AppContext appContext) {
		Renderer.super.init(appContext);
		DeferredRenderer renderer = this;

		drawThread = new TimeStepThread("Renderer", Config.LOCK_FPS ? 0.03f : 0.0f) {
			public void cleanUp() {
				renderer.destroy();
			}
			public void update(float seconds) {
				Thread.currentThread().setName(Renderer.RENDER_THREAD_NAME);
				if (!initialized) {
					setCurrentState("INITIALIZING");
					setupOpenGL(headless);
					renderer.appContext = appContext;
					appContext.setRenderer(renderer);
					objLoader = new OBJLoader(renderer);
					textureFactory = new TextureFactory(renderer);
					DeferredRenderer.exitOnGLError("After TextureFactory");
					programFactory = new ProgramFactory(appContext);
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
					lightFactory = new LightFactory(appContext);
					environmentProbeFactory = new EnvironmentProbeFactory(appContext);
					gBuffer.init(renderer);

					sphereModel = null;
					try {
						sphereModel = objLoader.loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/sphere.obj")).get(0);
						sphereModel.setMaterial(getMaterialFactory().getDefaultMaterial());
					} catch (IOException e) {
						e.printStackTrace();
					} catch (Exception e) {
						e.printStackTrace();
					}
					initialized = true;
				}

				//TODO: throws illegalstateexception
				if (Display.isCreated() && !Display.isCloseRequested()) {
					if (appContext.isInitialized()) {
						GPUProfiler.startFrame();
						if(appContext.getScene().isInitialized())
						{
							draw(appContext);
						}
						Display.update();

						GPUProfiler.start("Update");
						renderer.update(appContext, seconds);
						GPUProfiler.end();
						GPUProfiler.endFrame();
						dumpTimings();
					}
				}
			}
		};
		renderThread.submit(drawThread);
	}

	private void setupOpenGL(boolean headless) {
		try {
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
//				frame.setVisible(true);
				frame.setVisible(false);
				Display.setParent(canvas);
//				openGLContext.attach(canvas);
			}
			openGLContext = new OpenGLContext(this, headless);

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

		gBuffer = new GBuffer(appContext, this);

		setMaxTextureUnits(GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS));
		openGLContext.enable(GlCap.TEXTURE_CUBE_MAP_SEAMLESS);

		DeferredRenderer.exitOnGLError("setupGBuffer");
	}
	
	private void initIdentityMatrixBuffer() {
		identityMatrix44Buffer = new Transform().getTransformationBuffer();
	}
	
	private void setupShaders() {
		DeferredRenderer.exitOnGLError("Before setupShaders");
		try {
			cubeMap = textureFactory.getCubeMap("hp/assets/textures/skybox.png");
			openGLContext.activeTexture(0);
			textureFactory.generateMipMapsCubeMap(cubeMap.getTextureID());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		renderToQuadProgram = programFactory.getProgram("passthrough_vertex.glsl", "simpletexture_fragment.glsl", RENDERTOQUAD, false);
		blurProgram = programFactory.getProgram("passthrough_vertex.glsl", "blur_fragment.glsl", RENDERTOQUAD, false);
		bilateralBlurProgram = programFactory.getProgram("passthrough_vertex.glsl", "blur_bilateral_fragment.glsl", RENDERTOQUAD, false);
		linesProgram = programFactory.getProgram("mvp_vertex.glsl", "simple_color_fragment.glsl");

		DeferredRenderer.exitOnGLError("setupShaders");
	}

	@Override
	public void init(Octree octree) {
		addCommand(new Command() {
			@Override
			public Result execute(AppContext appContext) {

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

	public void update(AppContext appContext, float seconds) {
		try {
			executeCommands(appContext);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	// I need this to force probe redrawing after engine startup....TODO: Find better solution
	int counter = 0;

	public void draw(AppContext appContext) {
		setLastFrameTime();

		if (Config.DRAWLINES_ENABLED) {
			debugDrawStrategy.draw(appContext);
		} else {
			simpleDrawStrategy.draw(appContext);
		}

		if (Config.DEBUGFRAME_ENABLED) {
//			drawToQuad(lightFactory.getDepthMapForAreaLight(lightFactory.getAreaLights().get(0)), debugBuffer);
			drawToQuad(appContext.getScene().getDirectionalLight().getShadowMapId(), debugBuffer);
//			drawToQuad(gBuffer.getNormalMap(), debugBuffer);
//			for(int i = 0; i < 6; i++) {
//				drawToQuad(environmentProbeFactory.getProbes().get(0).getSampler().getCubeMapFaceViews()[1][i], sixDebugBuffers.get(i));
//			}
//			drawToQuad(light.getShadowMapId(), debugBuffer);
		}

		if(counter < 20) {
			appContext.getScene().getDirectionalLight().rotate(new Vector4f(0, 1, 0, 0.001f));
			Config.CONTINUOUS_DRAW_PROBES = true;
			counter++;
		} else if(counter == 20) {
			Config.CONTINUOUS_DRAW_PROBES = false;
			counter++;
		}

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
		openGLContext.disable(GlCap.DEPTH_TEST);

		openGLContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, texture);
		openGLContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, gBuffer.getNormalMap());

		buffer.draw();
	}

	@Override
	public void blur2DTexture(int sourceTextureId, int mipmap, int width, int height, int internalFormat, boolean upscaleToFullscreen, int blurTimes) {
		GPUProfiler.start("BLURRRRRRR");
		int copyTextureId = GL11.glGenTextures();
		getOpenGLContext().bindTexture(0, GlTextureTarget.TEXTURE_2D, copyTextureId);

		GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, util.Util.calculateMipMapCount(Math.max(width, height)), internalFormat, width, height);
//		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
//		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
//		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
//		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, util.Util.calculateMipMapCount(Math.max(width,height)));
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

		GL43.glCopyImageSubData(sourceTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				width, height, 1);

		float scaleForShaderX = (float) (Config.WIDTH / width);
		float scaleForShaderY = (float) (Config.HEIGHT / height);
		// TODO: Reset texture sizes after upscaling!!!
		if(upscaleToFullscreen) {
			getOpenGLContext().bindTexture(0, GlTextureTarget.TEXTURE_2D, sourceTextureId);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, Config.WIDTH, Config.HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
			scaleForShaderX = 1;
			scaleForShaderY = 1;
		}

		fullScreenTarget.use(false);
		fullScreenTarget.setTargetTexture(sourceTextureId, 0);

		openGLContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, copyTextureId);

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
		openGLContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, copyTextureId);
		
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		
		GL43.glCopyImageSubData(sourceTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				width, height, 1);
		
		float scaleForShaderX = (float) (Config.WIDTH / width);
		float scaleForShaderY = (float) (Config.HEIGHT / height);
		if(upscaleToFullscreen) {
			openGLContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, sourceTextureId);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, Config.WIDTH, Config.HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
			scaleForShaderX = 1;
			scaleForShaderY = 1;
		}

		fullScreenTarget.use(false);
		fullScreenTarget.setTargetTexture(sourceTextureId, 0);

		openGLContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, copyTextureId);

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
        try {
            Display.destroy();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
	}

	private static long lastFrameTime = 0l;

	private void setLastFrameTime() {
		lastFrameTime = getTime();
		Display.setTitle(String.format("Render %03.0f fps | %03.0f ms --- Update %03.0f fps | %03.0f ms" , drawThread.getFpsCounter().getFPS(), drawThread.getFpsCounter().getMsPerFrame(),
				AppContext.getInstance().getFPSCounter().getFPS(), AppContext.getInstance().getFPSCounter().getMsPerFrame()));
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
	public void batchLine(Vector3f from, Vector3f to) {
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
	private int rsmSize = 2048/2/2/2/2/2/2/2;
	private JFrame frame;

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


	private void executeCommands(AppContext appContext) throws Exception {
        Command command = workQueue.poll();
        while(command != null) {
			setCurrentState("BEFORE EXECUTION " + command.getClass().getSimpleName());
        	Result result = command.execute(appContext);
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
		
		renderProbeCommandQueue.takeNearest(appContext.getActiveCamera()).ifPresent(command -> {
			command.getProbe().draw(appContext, command.isUrgent());
		});
		counter++;
		
		while(counter < RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL) {
			renderProbeCommandQueue.take().ifPresent(command -> {
                command.getProbe().draw(appContext, command.isUrgent());
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
		return drawThread.getFpsCounter().getFPS();
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
		for (Entity entity : appContext.getScene().getPointLights()) {
			entity.setHasMoved(false);
		}
		for (Entity entity : appContext.getScene().getAreaLights()) {
			entity.setHasMoved(false);
		}

		appContext.getScene().getDirectionalLight().setHasMoved(false);
	}

	private void setCurrentState(String newState) {
		currentState = newState;
		AppContext.getEventBus().post(new StateChangedEvent(newState));
	}

	protected void finalize() throws Throwable {
		destroy();
	}

	@Override
	public void setAppContext(AppContext appContext) {
		this.appContext = appContext;
	}

	@Override
	public AppContext getAppContext() {
		return appContext;
	}

	@Override
	public GBuffer getGBuffer() {
		return gBuffer;
	}

	@Override
	public VertexBuffer getFullscreenBuffer() {
		return fullscreenBuffer;
	}


    @Override
    public void doWithOpenGLContext(Runnable runnable) {
        doWithOpenGLContext(runnable, true);
    }
	@Override
	public void doWithOpenGLContext(Runnable runnable, boolean andBlock) {

		if(util.Util.isRenderThread()) {
			runnable.run();
		} else {
//			SynchronousQueue<Result<OpenGLThread>> queue = addCommand(new Command<Result<OpenGLThread>>() {
//				@Override
//				public Result<OpenGLThread> execute(AppContext world) {
//					return new Result(new OpenGLThread("Create texture thread") {
//						@Override
//						public void doRun() {
//							runnable.run();
//						}
//					});
//				}
//			});
//			OpenGLThread thread = null;
//			try {
//				thread = queue.poll(5, TimeUnit.MINUTES).get();
//				thread.start();
//                if(andBlock) {
//                    thread.join();
//                }
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}

            SynchronousQueue<Result<Object>> queue = appContext.getRenderer().addCommand(new Command<Result<Object>>() {
                 @Override
                 public Result<Object> execute(AppContext appContext) {
                     runnable.run();
                     return new Result<Object>(true);
                 }
             }
            );
            try {
                queue.poll(5, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
	}

    @Override
    public <TYPE> TYPE calculateWithOpenGLContext(Callable<TYPE> callable) {
        if(util.Util.isRenderThread()) {
            try {
                return callable.call();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            final TYPE[] temp = (TYPE[])new Object[1];
            SynchronousQueue<Result<Object>> queue = appContext.getRenderer().addCommand(new Command<Result<Object>>() {
                 @Override
                 public Result<Object> execute(AppContext appContext) {
                     try {
                         temp[0] = callable.call();
                     } catch (Exception e) {
                         e.printStackTrace();
                     }
                     return new Result<Object>(true);
                 }
             }
            );
            try {
                queue.poll(5, TimeUnit.MINUTES);
                return temp[0];
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

	@Override
	public TimeStepThread getDrawThread() {
		return drawThread;
	}

	@Override
	public OpenGLContext getOpenGLContext() {
		return openGLContext;
	}
}
