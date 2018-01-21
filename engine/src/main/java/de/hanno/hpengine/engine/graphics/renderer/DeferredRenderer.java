package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.light.LightFactory;
import de.hanno.hpengine.engine.graphics.renderer.command.RenderProbeCommandQueue;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.GBuffer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SimpleDrawStrategy;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.GPUCulledMainPipeline;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.QuadVertexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;
import de.hanno.hpengine.engine.scene.EnvironmentProbeFactory;
import de.hanno.hpengine.util.fps.FPSCounter;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import org.joml.Vector3f;

import javax.vecmath.Vector2f;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static de.hanno.hpengine.log.ConsoleLogger.getLogger;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.opengl.GL11.glFinish;

public class DeferredRenderer implements Renderer {
	private static Logger LOGGER = getLogger();

	private volatile boolean initialized = false;

	private RenderProbeCommandQueue renderProbeCommandQueue = new RenderProbeCommandQueue();

	private ArrayList<VertexBuffer> sixDebugBuffers;

	private GBuffer gBuffer;
	private SimpleDrawStrategy simpleDrawStrategy;

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
            setupBuffers();
            GraphicsContext.exitOnGLError("After TextureFactory");
            try {
                setupShaders();
                setUpGBuffer();
                simpleDrawStrategy = new SimpleDrawStrategy();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Cannot init DeferredRenderer");
                System.exit(-1);
            }
            LightFactory.init();
            EnvironmentProbeFactory.init();
            gBuffer.init();

            float[] points = {0f, 0f, 0f, 0f};
            buffer = new VertexBuffer(points, EnumSet.of(DataChannels.POSITION3));
			buffer.upload();
			initialized = true;
        }
	}

	private void setupBuffers() {

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

		GraphicsContext.exitOnGLError("setupBuffers");
	}

    private void setUpGBuffer() {
		GraphicsContext.exitOnGLError("Before setupGBuffer");

		gBuffer = GraphicsContext.getInstance().calculate(() -> new GBuffer());

        GraphicsContext.getInstance().execute(() -> {
            GraphicsContext.getInstance().enable(GlCap.TEXTURE_CUBE_MAP_SEAMLESS);

			GraphicsContext.exitOnGLError("setupGBuffer");
		});
	}
	
	private void setupShaders() throws Exception {
		GraphicsContext.exitOnGLError("Before setupShaders");
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
//			drawToQuad(162, QuadVertexBuffer.getDebugBuffer(), ProgramFactory.getInstance().getDebugFrameProgram());
//			drawToQuad(gBuffer.getVisibilityMap(), QuadVertexBuffer.getDebugBuffer());
			drawToQuad(simpleDrawStrategy.getDirectionalLightExtension().getShadowMapId(), QuadVertexBuffer.getDebugBuffer());
			for(int i = 0; i < 6; i++) {
//                drawToQuad(simpleDrawStrategy.getLightMapExtension().getSamplers().get(32).getCubeMapFaceViews()[i], sixDebugBuffers.get(i));
//                drawToQuad(EnvironmentProbeFactory.getInstance().getProbes().get(0).getSampler().getCubeMapFaceViews()[3][i], sixDebugBuffers.get(i));
			}

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

		GPUProfiler.start("Create new fence");
		GraphicsContext.getInstance().createNewGPUFenceForReadState(renderState);
		GPUProfiler.end();
		GPUProfiler.start("Waiting for driver");
		GPUProfiler.start("Poll events");
		glfwPollEvents();
		GPUProfiler.end();
		GPUProfiler.start("Swap buffers");
		glfwSwapBuffers(GraphicsContext.getInstance().getWindowHandle());
		GPUProfiler.end();
		GPUProfiler.end();
		GPUProfiler.end();
	}

	@Override
	public void drawToQuad(int texture) {
		drawToQuad(texture, QuadVertexBuffer.getFullscreenBuffer(), ProgramFactory.getInstance().getRenderToQuadProgram());
	}

	public void drawToQuad(int texture, VertexBuffer buffer) {
		drawToQuad(texture, buffer, ProgramFactory.getInstance().getRenderToQuadProgram());
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer, Program program) {
		program.use();
        GraphicsContext.getInstance().disable(GlCap.DEPTH_TEST);

        GraphicsContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, texture);
        GraphicsContext.getInstance().bindTexture(1, GlTextureTarget.TEXTURE_2D, gBuffer.getNormalMap());

		buffer.draw();
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
	public void addRenderProbeCommand(EnvironmentProbe probe, boolean urgent) {
		renderProbeCommandQueue.addProbeRenderCommand(probe, urgent);
	}

    @Override
    public FPSCounter getFPSCounter() {
        return fpsCounter;
    }

	public float getCurrentFPS() {
        return fpsCounter.getFPS();
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
	public void registerPipelines(TripleBuffer<RenderState> renderState) {
        simpleDrawStrategy.setMainPipelineRef(renderState.registerPipeline(GPUCulledMainPipeline::new));
	}

}
