package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.SimpleTransform;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.light.LightFactory;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.*;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.event.StateChangedEvent;
import de.hanno.hpengine.engine.graphics.renderer.command.Command;
import de.hanno.hpengine.engine.graphics.renderer.command.RenderProbeCommandQueue;
import de.hanno.hpengine.engine.graphics.renderer.command.Result;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.util.fps.FPSCounter;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;
import de.hanno.hpengine.engine.scene.EnvironmentProbeFactory;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import de.hanno.hpengine.util.stopwatch.OpenGLStopWatch;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.joml.Vector3f;

import javax.vecmath.Vector2f;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.SynchronousQueue;
import java.util.logging.Logger;

import static de.hanno.hpengine.log.ConsoleLogger.getLogger;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.opengl.GL11.glFinish;

public class DeferredRenderer implements Renderer {
	private static boolean IGNORE_GL_ERRORS = false;

	private static Logger LOGGER = getLogger();

	private volatile boolean initialized = false;

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

	private ArrayList<VertexBuffer> sixDebugBuffers;

	private OBJLoader objLoader;

	private StaticModel sphereMesh;

	private FloatBuffer identityMatrix44Buffer;

	private GBuffer gBuffer;
	private SimpleDrawStrategy simpleDrawStrategy;
	private DrawStrategy currentDrawStrategy;

	private RenderTarget fullScreenTarget;
	private RenderTarget halfScreenTarget;

	private int maxTextureUnits;
	private String currentState = "";

    VertexBuffer buffer;

    private FPSCounter fpsCounter = new FPSCounter();

    public DeferredRenderer() {
    }

	@Override
	public void init() {
    	if(!(GraphicsContext.getInstance() instanceof OpenGLContext)) {
    		throw new IllegalStateException("Cannot use this DeferredRenderer with a non-OpenGlContext!");
		}

		Renderer.super.init();

        if (!initialized) {
            setCurrentState("INITIALIZING");
            setupBuffers();
            objLoader = new OBJLoader();
            DeferredRenderer.exitOnGLError("After TextureFactory");
            try {
                setupShaders();
                setUpGBuffer();
                simpleDrawStrategy = new SimpleDrawStrategy();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Cannot init DeferredRenderer");
                System.exit(-1);
            }
            currentDrawStrategy = simpleDrawStrategy;

            fullScreenTarget = new RenderTargetBuilder().setWidth(Config.getInstance().getWidth())
                                        .setHeight(Config.getInstance().getHeight())
                                        .add(new ColorAttachmentDefinition().setInternalFormat(GL11.GL_RGBA8))
                                        .build();
            LightFactory.init();
            EnvironmentProbeFactory.init();
            gBuffer.init();

            sphereMesh = null;
            try {
                sphereMesh = objLoader.loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj"));
                sphereMesh.setMaterial(MaterialFactory.getInstance().getDefaultMaterial());
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }

            float[] points = {0f, 0f, 0f, 0f};
            buffer = new VertexBuffer(points, EnumSet.of(DataChannels.POSITION3));
			buffer.upload();
			initialized = true;
        }
	}

	private void setupBuffers() {

        initIdentityMatrixBuffer();

		sixDebugBuffers = new ArrayList<VertexBuffer>() {{
			float height = -2f/3f;
			float width = 2f;
			float widthDiv = width/6f;
			for (int i = 0; i < 6; i++) {
				QuadVertexBuffer quadVertexBuffer = new QuadVertexBuffer(new Vector2f(-1f + i * widthDiv, -1f), new Vector2f(-1 + (i + 1) * widthDiv, height));
				add(quadVertexBuffer);
				quadVertexBuffer.upload();
			}
		}};
		glWatch = new OpenGLStopWatch();

		DeferredRenderer.exitOnGLError("setupBuffers");
//		CLUtil.initialize();
	}

    private void setUpGBuffer() {
		DeferredRenderer.exitOnGLError("Before setupGBuffer");

		gBuffer = GraphicsContext.getInstance().calculate(() -> new GBuffer());

        GraphicsContext.getInstance().execute(() -> {
            GraphicsContext.getInstance().enable(GlCap.TEXTURE_CUBE_MAP_SEAMLESS);

			DeferredRenderer.exitOnGLError("setupGBuffer");
		});
	}
	
	private void initIdentityMatrixBuffer() {
		identityMatrix44Buffer = new SimpleTransform().getTransformationBuffer();
	}
	
	private void setupShaders() throws Exception {
		DeferredRenderer.exitOnGLError("Before setupShaders");

		renderToQuadProgram = ProgramFactory.getInstance().getProgram(false, Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "passthrough_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "simpletexture_fragment.glsl")));
		blurProgram = ProgramFactory.getInstance().getProgram(false, Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "passthrough_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "blur_fragment.glsl")));
		bilateralBlurProgram = ProgramFactory.getInstance().getProgram(false, Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "passthrough_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "blur_bilateral_fragment.glsl")));
		linesProgram = ProgramFactory.getInstance().getProgram("mvp_vertex.glsl", "simple_color_fragment.glsl");

	}

	public void update(float seconds) {
	}


    @Override
	public void draw(DrawResult result, RenderState renderState) {
		GPUProfiler.start("Frame");
//		TODO: Reimplement this with a custom field for probes
//		if(renderState.directionalLightNeedsShadowMapRender) {
//			EnvironmentProbeFactory.getInstance().draw(true);
//		}
        simpleDrawStrategy.draw(result, renderState);
		if (Config.getInstance().isDebugframeEnabled()) {
//            drawToQuad(gBuffer.getLightAccumulationMapOneId(), QuadVertexBuffer.getDebugBuffer());
//            drawToQuad(gBuffer.getColorReflectivenessMap(), QuadVertexBuffer.getDebugBuffer());
			drawToQuad(simpleDrawStrategy.getDirectionalLightExtension().getShadowMapId(), QuadVertexBuffer.getDebugBuffer());
//			for(int i = 0; i < 6; i++) {
//                drawToQuad(simpleDrawStrategy.getLightMapExtension().getSamplers().get(32).getCubeMapFaceViews()[i], sixDebugBuffers.get(i));
////                drawToQuad(EnvironmentProbeFactory.getInstance().getProbes().get(0).getSampler().getCubeMapFaceViews()[3][i], sixDebugBuffers.get(i));
//			}

//			int faceView = OpenGLContext.getInstance().genTextures();
//			GL43.glTextureView(faceView, GlTextureTarget.TEXTURE_2D.glTarget, lightFactory.getPointLightDepthMapsArrayBack(),
//					GL30.GL_RGBA16F, 0, 1, 0, 1);
//			drawToQuad(faceView, sixDebugBuffers.get(0));
//			faceView = OpenGLContext.getInstance().genTextures();
//			GL43.glTextureView(faceView, GlTextureTarget.TEXTURE_2D.glTarget, lightFactory.getPointLightDepthMapsArrayFront(),
//					GL30.GL_RGBA16F, 0, 1, 0, 1);
//			drawToQuad(faceView, sixDebugBuffers.get(1));
//			GL11.glDeleteTextures(faceView);

//            int[] faceViews = new int[6];
//            for(int i = 0; i < 6; i++) {
//                faceViews[i] = OpenGLContext.getInstance().genTextures();
//                GL43.glTextureView(faceViews[i], GlTextureTarget.TEXTURE_2D.glTarget, lightFactory.getCubemapArrayRenderTarget().getDepthBufferTexture(),
//						GL14.GL_DEPTH_COMPONENT24, 0, 1, 6+i, 1);
//				drawToQuad(faceViews[i], sixDebugBuffers.get(i));
//			}
//            for(int i = 0; i < 6; i++) {
//                GL11.glDeleteTextures(faceViews[i]);
//            }
		}

//		if(counter < 20) {
//            Engine.getInstance().getScene().getDirectionalLight().rotate(new Vector4f(0, 1, 0, 0.001f));
//			Config.getInstance().CONTINUOUS_DRAW_PROBES = true;
//			counter++;
//		} else if(counter == 20) {
//			Config.getInstance().CONTINUOUS_DRAW_PROBES = false;
//			counter++;
//		}

		GPUProfiler.start("Create new fence");
		GraphicsContext.getInstance().createNewGPUFenceForReadState(renderState);
		GPUProfiler.end();
        GPUProfiler.start("Waiting for driver");
		glfwPollEvents();
		glfwSwapBuffers(GraphicsContext.getInstance().getWindowHandle());
		GPUProfiler.end();
		GPUProfiler.end();
	}

	@Override
	public void drawToQuad(int texture) {
		drawToQuad(texture, QuadVertexBuffer.getFullscreenBuffer(), renderToQuadProgram);
	}

	public void drawToQuad(int texture, VertexBuffer buffer) {
		drawToQuad(texture, buffer, renderToQuadProgram);
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer, Program program) {
		program.use();
        GraphicsContext.getInstance().disable(GlCap.DEPTH_TEST);

        GraphicsContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, texture);
        GraphicsContext.getInstance().bindTexture(1, GlTextureTarget.TEXTURE_2D, gBuffer.getNormalMap());

		buffer.draw();
	}

	public void blur2DTexture(int sourceTextureId, int mipmap, int width, int height, int internalFormat, boolean upscaleToFullscreen, int blurTimes, RenderTarget target) {
		GPUProfiler.start("BLURRRRRRR");
		int copyTextureId = GL11.glGenTextures();
        GraphicsContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, copyTextureId);

		GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, de.hanno.hpengine.util.Util.calculateMipMapCount(Math.max(width, height)), internalFormat, width, height);
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

		float scaleForShaderX = (float) (Config.getInstance().getWidth() / width);
		float scaleForShaderY = (float) (Config.getInstance().getHeight() / height);
		// TODO: Reset de.hanno.hpengine.texture sizes after upscaling!!!
		if(upscaleToFullscreen) {
            GraphicsContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, sourceTextureId);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, Config.getInstance().getWidth(), Config.getInstance().getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
			scaleForShaderX = 1;
			scaleForShaderY = 1;
		}

		target.use(false);
        target.setTargetTexture(sourceTextureId, 0);

        GraphicsContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, copyTextureId);

		blurProgram.use();
		blurProgram.setUniform("mipmap", mipmap);
		blurProgram.setUniform("scaleX", scaleForShaderX);
		blurProgram.setUniform("scaleY", scaleForShaderY);
        QuadVertexBuffer.getFullscreenBuffer().draw();
        target.unuse();
		GL11.glDeleteTextures(copyTextureId);
		GPUProfiler.end();
	}

	public void blur2DTextureBilateral(int sourceTextureId, int edgeTexture, int width, int height, int internalFormat, boolean upscaleToFullscreen, int blurTimes) {
		int copyTextureId = GL11.glGenTextures();
        GraphicsContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, copyTextureId);
		
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		
		GL43.glCopyImageSubData(sourceTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				width, height, 1);
		
		float scaleForShaderX = (float) (Config.getInstance().getWidth() / width);
		float scaleForShaderY = (float) (Config.getInstance().getHeight() / height);
		if(upscaleToFullscreen) {
            GraphicsContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, sourceTextureId);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, Config.getInstance().getWidth(), Config.getInstance().getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
			scaleForShaderX = 1;
			scaleForShaderY = 1;
		}

		fullScreenTarget.use(false);
		fullScreenTarget.setTargetTexture(sourceTextureId, 0);

        GraphicsContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, copyTextureId);

		bilateralBlurProgram.use();
		bilateralBlurProgram.setUniform("scaleX", scaleForShaderX);
		bilateralBlurProgram.setUniform("scaleY", scaleForShaderY);
        QuadVertexBuffer.getFullscreenBuffer().draw();
		fullScreenTarget.unuse();
		GL11.glDeleteTextures(copyTextureId);
	}

	private static long lastFrameTime = 0l;

	private void setLastFrameTime() {
		lastFrameTime = getTime();
        fpsCounter.update();
	}
	private long getTime() {
		return System.currentTimeMillis();
	}
	public double getDeltaInMS() {
		long currentTime = getTime();
		double delta = (double) currentTime - (double) lastFrameTime;
		return delta;
	}
	@Override
	public double getDeltaInS() {
		return (getDeltaInMS() / 1000d);
	}

	public static void exitOnGLError(String errorMessage) {
        Exception exception = new Exception();
        GraphicsContext.getInstance().execute(() -> {
            if(IGNORE_GL_ERRORS) { return; }
            int errorValue = GL11.glGetError();

            if (errorValue != GL11.GL_NO_ERROR) {
                String errorString = GLU.gluErrorString(errorValue);
                exception.printStackTrace(System.err);
                System.err.println("ERROR - " + errorMessage + ": " + errorString);

                System.exit(-1);
            }
        });
	}

	@Override
	public boolean isInitialized() {
		return initialized;
	}

	public int drawLines(Program program) {

		float[] points = new float[linePoints.size() * 3];
		for (int i = 0; i < linePoints.size(); i++) {
			Vector3f point = linePoints.get(i);
			points[3*i + 0] = point.x;
			points[3*i + 1] = point.y;
			points[3*i + 2] = point.z;
		}
		buffer.putValues(points);
		buffer.upload().join();
		buffer.drawDebugLines();
		glFinish();
		linePoints.clear();
        return points.length / 3 / 2;
	}

	@Override
	public void batchLine(Vector3f from, Vector3f to) {
		linePoints.add(from);
		linePoints.add(to);
	}

	private List<Vector3f> linePoints = new CopyOnWriteArrayList<>();

	public Model getSphere() {
		return sphereMesh;
	}


	@Override
	public void executeRenderProbeCommands(RenderState extract) {
		int counter = 0;
		
		renderProbeCommandQueue.takeNearest(extract.camera).ifPresent(command -> {
			command.getProbe().draw(command.isUrgent(), extract);
		});
		counter++;
		
		while(counter < RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL) {
			renderProbeCommandQueue.take().ifPresent(command -> {
                command.getProbe().draw(command.isUrgent(), extract);
            });
			counter++;
		}
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
    public FPSCounter getFPSCounter() {
        return fpsCounter;
    }

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

	private void setCurrentState(String newState) {
		currentState = newState;
		Engine.getEventBus().post(new StateChangedEvent(newState));
	}

	protected void finalize() throws Throwable {
		destroy();
	}

    @Override
	public GBuffer getGBuffer() {
		return gBuffer;
	}

    @Override
    public void endFrame() {
        GPUProfiler.endFrame();
		setLastFrameTime();
    }

    @Override
	public void startFrame() {
        GPUProfiler.startFrame();
	}

	@Override
	public void registerPipelines(TripleBuffer<RenderState> renderstate) {
		simpleDrawStrategy.setPipelineIndex(renderstate.registerPipeline(() -> new Pipeline() {

			@Override
			public void drawIndirectStatic(RenderState renderState, Program program) {
				beforeDraw(renderState, program);
				super.drawIndirectStatic(renderState, program);
			}

			@Override
			public void drawIndirectAnimated(RenderState renderState, Program program) {
				beforeDraw(renderState, program);
				super.drawIndirectAnimated(renderState, program);
			}

			private void beforeDraw(RenderState renderState, Program program) {
				Camera camera = renderState.camera;
				FloatBuffer viewMatrixAsBuffer = camera.getViewMatrixAsBuffer();
				FloatBuffer projectionMatrixAsBuffer = camera.getProjectionMatrixAsBuffer();
				FloatBuffer viewProjectionMatrixAsBuffer = camera.getViewProjectionMatrixAsBuffer();

				program.use();
				program.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
				program.bindShaderStorageBuffer(3, renderState.getEntitiesBuffer());
				program.setUniform("useRainEffect", Config.getInstance().getRainEffect() == 0.0 ? false : true);
				program.setUniform("rainEffect", Config.getInstance().getRainEffect());
				program.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
				program.setUniformAsMatrix4("lastViewMatrix", viewMatrixAsBuffer);
				program.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
				program.setUniformAsMatrix4("viewProjectionMatrix", viewProjectionMatrixAsBuffer);
				program.setUniform("eyePosition", camera.getPosition());
				program.setUniform("lightDirection", renderState.directionalLightState.directionalLightDirection);
				program.setUniform("near", camera.getNear());
				program.setUniform("far", camera.getFar());
				program.setUniform("time", (int) System.currentTimeMillis());
				program.setUniform("useParallax", Config.getInstance().isUseParallax());
				program.setUniform("useSteepParallax", Config.getInstance().isUseSteepParallax());
			}
		}));
	}

}
