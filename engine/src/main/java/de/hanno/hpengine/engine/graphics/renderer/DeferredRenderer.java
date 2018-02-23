package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.command.RenderProbeCommandQueue;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.GBuffer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SimpleDrawStrategy;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.GPUCulledMainPipeline;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.QuadVertexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import org.joml.Vector3f;

import javax.vecmath.Vector2f;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;

import static de.hanno.hpengine.log.ConsoleLogger.getLogger;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.opengl.GL11.glFinish;

public class DeferredRenderer implements Renderer {
	private static Logger LOGGER = getLogger();

	private ArrayList<VertexBuffer> sixDebugBuffers;

	private GBuffer gBuffer;
	private SimpleDrawStrategy simpleDrawStrategy;

    VertexBuffer buffer;

	private Engine engine;

	public DeferredRenderer() { }

	@Override
	public void init(Engine engine) {
        if(!(engine.getGpuContext() instanceof OpenGLContext)) {
    		throw new IllegalStateException("Cannot use this DeferredRenderer with a non-OpenGlContext!");
		}
		this.engine = engine;

		setupBuffers();
		GpuContext.exitOnGLError("After TextureManager");
		try {
			GpuContext.exitOnGLError("Before setupShaders");
			setUpGBuffer();
			simpleDrawStrategy = new SimpleDrawStrategy(engine);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Cannot init DeferredRenderer");
			System.exit(-1);
		}

		float[] points = {0f, 0f, 0f, 0f};
		buffer = new VertexBuffer(engine.getGpuContext(), points, EnumSet.of(DataChannels.POSITION3));
		buffer.upload();
	}

	private void setupBuffers() {

		sixDebugBuffers = new ArrayList<VertexBuffer>() {{
			float height = -2f/3f;
			float width = 2f;
			float widthDiv = width/6f;
			for (int i = 0; i < 6; i++) {
				QuadVertexBuffer quadVertexBuffer = new QuadVertexBuffer(engine.getGpuContext(), new Vector2f(-1f + i * widthDiv, -1f), new Vector2f(-1 + (i + 1) * widthDiv, height));
				add(quadVertexBuffer);
				quadVertexBuffer.upload();
			}
		}};

		GpuContext.exitOnGLError("setupBuffers");
	}

    private void setUpGBuffer() {
		GpuContext.exitOnGLError("Before setupGBuffer");

        gBuffer = engine.getGpuContext().calculate(() -> new GBuffer(engine.getGpuContext()));

        engine.getGpuContext().execute(() -> {
            engine.getGpuContext().enable(GlCap.TEXTURE_CUBE_MAP_SEAMLESS);

			GpuContext.exitOnGLError("setupGBuffer");
		});
	}

	public void update(Engine engine, float seconds) {
	}


    @Override
	public void draw(DrawResult result, RenderState renderState) {
		GPUProfiler.start("Frame");
//		TODO: Reimplement this with a custom field for probes
//		if(renderState.directionalLightNeedsShadowMapRender) {
//			EnvironmentProbeManager.getInstance().draw(true);
//		}

		engine.getSceneManager().getScene().getEnvironmentProbeManager().drawAlternating(renderState.getCamera().getEntity());
        simpleDrawStrategy.draw(result, renderState);
		if (Config.getInstance().isDebugframeEnabled()) {
//			drawToQuad(162, QuadVertexBuffer.getDebugBuffer(), ProgramManager.getInstance().getDebugFrameProgram());
//			drawToQuad(gBuffer.getVisibilityMap(), QuadVertexBuffer.getDebugBuffer());
			drawToQuad(simpleDrawStrategy.getDirectionalLightExtension().getShadowMapId(), engine.getGpuContext().getDebugBuffer());
			for(int i = 0; i < 6; i++) {
//                drawToQuad(EnvironmentProbeManager.getInstance().getProbes().get(0).getSampler().getCubeMapFaceViews()[3][i], sixDebugBuffers.get(i));
			}

//			int faceView = OpenGLContext.getInstance().genTextures();
//			GL43.glTextureView(faceView, GlTextureTarget.TEXTURE_2D.glTarget, lightManager.getPointLightDepthMapsArrayBack(),
//					GL30.GL_RGBA16F, 0, 1, 0, 1);
//			drawToQuad(faceView, sixDebugBuffers.get(0));
//			faceView = OpenGLContext.getInstance().genTextures();
//			GL43.glTextureView(faceView, GlTextureTarget.TEXTURE_2D.glTarget, lightManager.getPointLightDepthMapsArrayFront(),
//					GL30.GL_RGBA16F, 0, 1, 0, 1);
//			drawToQuad(faceView, sixDebugBuffers.get(1));
//			GL11.glDeleteTextures(faceView);

//            int[] faceViews = new int[6];
//            for(int i = 0; i < 6; i++) {
//                faceViews[i] = OpenGLContext.getInstance().genTextures();
//                GL43.glTextureView(faceViews[i], GlTextureTarget.TEXTURE_2D.glTarget, lightManager.getCubemapArrayRenderTarget().getDepthBufferTexture(),
//						GL14.GL_DEPTH_COMPONENT24, 0, 1, 6+i, 1);
//				drawToQuad(faceViews[i], sixDebugBuffers.get(i));
//			}
//            for(int i = 0; i < 6; i++) {
//                GL11.glDeleteTextures(faceViews[i]);
//            }
		}

		GPUProfiler.start("Create new fence");
        engine.getGpuContext().createNewGPUFenceForReadState(renderState);
		GPUProfiler.end();
		GPUProfiler.start("Waiting for driver");
		GPUProfiler.start("Poll events");
		glfwPollEvents();
		GPUProfiler.end();
		GPUProfiler.start("Swap buffers");
        glfwSwapBuffers(engine.getGpuContext().getWindowHandle());
		GPUProfiler.end();
		GPUProfiler.end();
		GPUProfiler.end();
	}

	@Override
	public void drawToQuad(int texture) {
        drawToQuad(texture, engine.getGpuContext().getFullscreenBuffer(), engine.getProgramManager().getRenderToQuadProgram());
	}

	public void drawToQuad(int texture, VertexBuffer buffer) {
        drawToQuad(texture, buffer, engine.getProgramManager().getRenderToQuadProgram());
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer, Program program) {
		program.use();
        engine.getGpuContext().disable(GlCap.DEPTH_TEST);

        engine.getGpuContext().bindTexture(0, GlTextureTarget.TEXTURE_2D, texture);
        engine.getGpuContext().bindTexture(1, GlTextureTarget.TEXTURE_2D, gBuffer.getNormalMap());

		buffer.draw();
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

	private List<Vector3f> linePoints = new ArrayList<>();

    @Override
	public GBuffer getGBuffer() {
		return gBuffer;
	}

    @Override
	public void registerPipelines(TripleBuffer<RenderState> renderState) {
		TripleBuffer.PipelineRef<GPUCulledMainPipeline> mainPipelineRef = renderState.registerPipeline(() -> new GPUCulledMainPipeline(engine));
		simpleDrawStrategy.setMainPipelineRef(mainPipelineRef);
	}

}
