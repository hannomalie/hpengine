package de.hanno.hpengine.renderer;

import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.event.StateChangedEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Vector3f;
import de.hanno.hpengine.renderer.command.Command;
import de.hanno.hpengine.renderer.command.RenderProbeCommandQueue;
import de.hanno.hpengine.renderer.command.Result;
import de.hanno.hpengine.renderer.constants.GlCap;
import de.hanno.hpengine.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.renderer.drawstrategy.*;
import de.hanno.hpengine.renderer.fps.FPSCounter;
import de.hanno.hpengine.renderer.light.LightFactory;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.renderer.rendertarget.ColorAttachmentDefinition;
import de.hanno.hpengine.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.renderer.rendertarget.RenderTargetBuilder;
import de.hanno.hpengine.scene.EnvironmentProbe;
import de.hanno.hpengine.scene.EnvironmentProbeFactory;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.shader.ProgramFactory;
import de.hanno.hpengine.texture.TextureFactory;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import de.hanno.hpengine.util.stopwatch.OpenGLStopWatch;

import javax.vecmath.Vector2f;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static de.hanno.hpengine.log.ConsoleLogger.getLogger;

public class DeferredRenderer implements Renderer {
	private static boolean IGNORE_GL_ERRORS = false;
	private int frameCount = 0;

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

	private Model sphereModel;

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
		Renderer.super.init();

        if (!initialized) {
            setCurrentState("INITIALIZING");
            setupBuffers();
            objLoader = new OBJLoader();
            ProgramFactory.init();
            TextureFactory.init();
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

            fullScreenTarget = new RenderTargetBuilder().setWidth(Config.WIDTH)
                                        .setHeight(Config.HEIGHT)
                                        .add(new ColorAttachmentDefinition().setInternalFormat(GL11.GL_RGBA8))
                                        .build();
            MaterialFactory.init();
            LightFactory.init();
            EnvironmentProbeFactory.init();
            gBuffer.init();

            sphereModel = null;
            try {
                sphereModel = objLoader.loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/sphere.obj")).get(0);
                sphereModel.setMaterial(MaterialFactory.getInstance().getDefaultMaterial());
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }

            float[] points = {0f, 0f, 0f, 0f};
            buffer = new VertexBuffer(points, EnumSet.of(DataChannels.POSITION3)).upload();
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
				add(new QuadVertexBuffer(new Vector2f(-1f + i * widthDiv, -1f), new Vector2f(-1 + (i + 1) * widthDiv, height)).upload());
			}
		}};
		glWatch = new OpenGLStopWatch();

		DeferredRenderer.exitOnGLError("setupBuffers");
//		CLUtil.initialize();
	}

    private void setUpGBuffer() {
		DeferredRenderer.exitOnGLError("Before setupGBuffer");

		gBuffer = OpenGLContext.getInstance().calculate(() -> new GBuffer());

		OpenGLContext.getInstance().execute(() -> {
			OpenGLContext.getInstance().enable(GlCap.TEXTURE_CUBE_MAP_SEAMLESS);

			DeferredRenderer.exitOnGLError("setupGBuffer");
		});
	}
	
	private void initIdentityMatrixBuffer() {
		identityMatrix44Buffer = new Transform().getTransformationBuffer();
	}
	
	private void setupShaders() throws Exception {
		DeferredRenderer.exitOnGLError("Before setupShaders");

		renderToQuadProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "simpletexture_fragment.glsl", false);
		blurProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "blur_fragment.glsl", false);
		bilateralBlurProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "blur_bilateral_fragment.glsl", false);
		linesProgram = ProgramFactory.getInstance().getProgram("mvp_vertex.glsl", "simple_color_fragment.glsl");

//		DeferredRenderer.exitOnGLError("setupShaders");
	}

	public void update(float seconds) {
	}


	// I need this to force probe redrawing after de.hanno.hpengine.engine startup....TODO: Find better solution
	int counter = 0;

    @Override
	public void draw(DrawResult result, RenderState renderState) {
		GPUProfiler.start("Frame");
		if(renderState.directionalLightNeedsShadowMapRender) {
			EnvironmentProbeFactory.getInstance().draw(true);
		}
        simpleDrawStrategy.draw(result, renderState);
		GPUProfiler.end();
		if (Config.DEBUGFRAME_ENABLED) {
//            drawToQuad(gBuffer.getLightAccumulationMapOneId(), QuadVertexBuffer.getDebugBuffer());
            drawToQuad(gBuffer.getColorReflectivenessMap(), QuadVertexBuffer.getDebugBuffer());
//			drawToQuad(simpleDrawStrategy.getDirectionalLightExtension().getShadowMapId(), QuadVertexBuffer.getDebugBuffer());
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
//			Config.CONTINUOUS_DRAW_PROBES = true;
//			counter++;
//		} else if(counter == 20) {
//			Config.CONTINUOUS_DRAW_PROBES = false;
//			counter++;
//		}

		frameCount++;
        GPUProfiler.start("Waiting for driver");
		Display.update();
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
        OpenGLContext.getInstance().disable(GlCap.DEPTH_TEST);

		OpenGLContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, texture);
		OpenGLContext.getInstance().bindTexture(1, GlTextureTarget.TEXTURE_2D, gBuffer.getNormalMap());

		buffer.draw();
	}

	public void blur2DTexture(int sourceTextureId, int mipmap, int width, int height, int internalFormat, boolean upscaleToFullscreen, int blurTimes, RenderTarget target) {
		GPUProfiler.start("BLURRRRRRR");
		int copyTextureId = GL11.glGenTextures();
		OpenGLContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, copyTextureId);

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

		float scaleForShaderX = (float) (Config.WIDTH / width);
		float scaleForShaderY = (float) (Config.HEIGHT / height);
		// TODO: Reset de.hanno.hpengine.texture sizes after upscaling!!!
		if(upscaleToFullscreen) {
			OpenGLContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, sourceTextureId);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, Config.WIDTH, Config.HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
			scaleForShaderX = 1;
			scaleForShaderY = 1;
		}

		target.use(false);
        target.setTargetTexture(sourceTextureId, 0);

		OpenGLContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, copyTextureId);

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
		OpenGLContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, copyTextureId);
		
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		
		GL43.glCopyImageSubData(sourceTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				width, height, 1);
		
		float scaleForShaderX = (float) (Config.WIDTH / width);
		float scaleForShaderY = (float) (Config.HEIGHT / height);
		if(upscaleToFullscreen) {
			OpenGLContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, sourceTextureId);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, Config.WIDTH, Config.HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
			scaleForShaderX = 1;
			scaleForShaderY = 1;
		}

		fullScreenTarget.use(false);
		fullScreenTarget.setTargetTexture(sourceTextureId, 0);

		OpenGLContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, copyTextureId);

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
        OpenGLContext.getInstance().execute(() -> {
            if(IGNORE_GL_ERRORS) { return; }
            int errorValue = GL11.glGetError();

            if (errorValue != GL11.GL_NO_ERROR) {
                String errorString = GLU.gluErrorString(errorValue);
                exception.printStackTrace(System.err);
                System.err.println("ERROR - " + errorMessage + ": " + errorString);

                if (Display.isCreated()) Display.destroy();
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
        buffer.upload();
        buffer.drawDebugLines();
		linePoints.clear();
        return points.length / 3 / 2;
	}

	@Override
	public void batchLine(Vector3f from, Vector3f to) {
		linePoints.add(from);
		linePoints.add(to);
	}

	private List<Vector3f> linePoints = new ArrayList<>();

	public Model getSphere() {
		return sphereModel;
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

	public int getFrameCount() {
		return frameCount;
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

}
