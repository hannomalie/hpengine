package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.OpenGLContext;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SimpleDrawStrategy;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.GPUCulledMainPipeline;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.ProgramManager;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.QuadVertexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.model.texture.Texture;
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

	private DeferredRenderingBuffer gBuffer;
	private SimpleDrawStrategy simpleDrawStrategy;

    private VertexBuffer buffer;

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

		TripleBuffer<RenderState> renderState = engine.getRenderManager().getRenderState();
		simpleDrawStrategy.setMainPipelineRef(renderState.registerState(() -> new GPUCulledMainPipeline(engine, DeferredRenderer.this)));
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

        gBuffer = engine.getGpuContext().calculate(() -> new DeferredRenderingBuffer(engine.getGpuContext()));

        engine.getGpuContext().execute(() -> {
            engine.getGpuContext().enable(GlCap.TEXTURE_CUBE_MAP_SEAMLESS);

			GpuContext.exitOnGLError("setupGBuffer");
		});
	}

	public void update(Engine engine, float seconds) {
	}


    @Override
	public void render(DrawResult result, RenderState renderState) {
		GPUProfiler.start("Frame");

		engine.getEnvironmentProbeManager().drawAlternating(renderState.getCamera().getEntity());
        simpleDrawStrategy.draw(result, null, renderState);
		if (Config.getInstance().isDebugframeEnabled()) {
			ArrayList<Texture> textures = new ArrayList<>(engine.getTextureManager().getTextures().values());
			drawToQuad(engine.getTextureManager().getTexture("hp/assets/models/textures/gi_flag.png", true).getTextureId(), engine.getGpuContext().getDebugBuffer(), engine.getProgramManager().getDebugFrameProgram());
//			drawToQuad(engine.getSimpleScene().getAreaLightSystem().getDepthMapForAreaLight(engine.getSimpleScene().getAreaLightSystem().getAreaLights().get(0)), engine.getGpuContext().getDebugBuffer(), engine.getProgramManager().getDebugFrameProgram());

//			for(int i = 0; i < 6; i++) {
//				drawToQuad(engine.getEnvironmentProbeManager().getProbes().get(0).getSampler().getCubeMapFaceViews()[3][i], sixDebugBuffers.get(i));
//			}


//			DEBUG POINT LIGHT SHADOWS

//			int faceView = OpenGLContext.getInstance().genTextures();
//			GL43.glTextureView(faceView, GlTextureTarget.TEXTURE_2D.glTarget, directionalLightSystem.getPointLightDepthMapsArrayBack(),
//					GL30.GL_RGBA16F, 0, 1, 0, 1);
//			drawToQuad(faceView, sixDebugBuffers.get(0));
//			faceView = OpenGLContext.getInstance().genTextures();
//			GL43.glTextureView(faceView, GlTextureTarget.TEXTURE_2D.glTarget, directionalLightSystem.getPointLightDepthMapsArrayFront(),
//					GL30.GL_RGBA16F, 0, 1, 0, 1);
//			drawToQuad(faceView, sixDebugBuffers.get(1));
//			GL11.glDeleteTextures(faceView);


//			DEBUG PROBES

//            int[] faceViews = new int[6];
//			int index = 0;
//            for(int i = 0; i < 6; i++) {
//                faceViews[i] = engine.getGpuContext().genTextures();
//				int cubeMapArray = engine.getScene().getProbeSystem().getStrategy().getCubemapArrayRenderTarget().getCubeMapArray().getTextureID();
//				GL43.glTextureView(faceViews[i], GlTextureTarget.TEXTURE_2D.glTarget, cubeMapArray, GL_RGBA16F, 0, 10, (6*index)+i, 1);
//				drawToQuad(faceViews[i], sixDebugBuffers.get(i), engine.getProgramManager().getDebugFrameProgram());
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
			points[3 * i] = point.x;
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
	public DeferredRenderingBuffer getGBuffer() {
		return gBuffer;
	}

	@Override
	public List<RenderExtension> getRenderExtensions() {
    	return simpleDrawStrategy.getRenderExtensions();
	}

}
