package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.backend.OpenGlBackend;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.OpenGlProgramManager;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.QuadVertexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.model.texture.Texture;
import de.hanno.hpengine.log.ConsoleLogger;
import de.hanno.hpengine.util.fps.FPSCounter;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import de.hanno.hpengine.util.stopwatch.OpenGLStopWatch;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import javax.vecmath.Vector2f;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static de.hanno.hpengine.engine.graphics.shader.ShaderKt.getShaderSource;

public class SimpleTextureRenderer implements Renderer<OpenGlBackend> {
	private static Logger LOGGER = ConsoleLogger.getLogger();

	private volatile boolean initialized = false;

	public static EnumSet<DataChannels> RENDERTOQUAD = EnumSet.of(
			DataChannels.POSITION3,
			DataChannels.TEXCOORD);

	private OpenGLStopWatch glWatch;

	private Program renderToQuadProgram;

	private ArrayList<VertexBuffer> sixDebugBuffers;

    private FPSCounter fpsCounter = new FPSCounter();
	private OpenGlProgramManager programManager;
	private GpuContext gpuContext;
	private Texture texture;

	public Texture getTexture() {
		return texture;
	}
	public void setTexture(Texture texture) {
		this.texture = texture;
	}

	public SimpleTextureRenderer(OpenGlProgramManager programManager, Texture texture) {
		this.texture = texture;
		init(programManager);
	}

	private void init(OpenGlProgramManager programManager) {
		this.programManager = programManager;
		this.gpuContext = this.programManager.getGpuContext();

        if (!initialized) {
            setCurrentState("INITIALIZING");
            setupBuffers(gpuContext);
            GpuContext.exitOnGLError("After TextureManager");
            try {
				GpuContext.exitOnGLError("Before setupShaders");

				renderToQuadProgram = this.programManager.getProgram(getShaderSource(new File(Shader.directory + "passthrough_vertex.glsl")), getShaderSource(new File(Shader.directory + "simpletexture_fragment.glsl")));

				setUpGBuffer();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Cannot init DeferredRenderer");
                System.exit(-1);
            }

            initialized = true;
        }
	}

	private void setupBuffers(GpuContext gpuContext) {

		sixDebugBuffers = new ArrayList<VertexBuffer>() {{
			float height = -2f/3f;
			float width = 2f;
			float widthDiv = width/6f;
			for (int i = 0; i < 6; i++) {
				QuadVertexBuffer quadVertexBuffer = new QuadVertexBuffer(gpuContext, new Vector2f(-1f + i * widthDiv, -1f), new Vector2f(-1 + (i + 1) * widthDiv, height));
				add(quadVertexBuffer);
				quadVertexBuffer.upload();
			}
		}};
		glWatch = new OpenGLStopWatch();

        GpuContext.exitOnGLError("setupBuffers");
	}

    private void setUpGBuffer() {
        GpuContext.exitOnGLError("Before setupGBuffer");

        gpuContext.execute(() -> {
            gpuContext.enable(GlCap.TEXTURE_CUBE_MAP_SEAMLESS);

			GpuContext.exitOnGLError("setupGBuffer");
		});
	}

	@Override
	public void destroy() {

	}

	public void update(Engine engine, float seconds) {
	}


	@Override
	public void render(@NotNull DrawResult result, @NotNull RenderState renderState) {
		GPUProfiler.start("Frame");
		drawToQuad(texture.getTextureId(), gpuContext.getFullscreenBuffer());
		GPUProfiler.end();
	}

	@Override
	public void drawToQuad(int texture) {
		drawToQuad(texture, gpuContext.getFullscreenBuffer(), renderToQuadProgram);
	}

	@Override
	public void batchVector(Vector3f vector) {

	}

	@Override
	public void batchVector(Vector3f vector, float charWidth) {

	}

	@Override
	public void batchString(String text) {

	}

	@Override
	public void batchString(String text, float charWidth) {

	}

	@Override
	public void batchString(String text, float charWidthIn, float gapIn) {

	}

	@Override
	public void batchString(String text, float charWidthIn, float gapIn, int x) {

	}

	@Override
	public void batchString(String text, float charWidthIn, float gapIn, float x, float y) {

	}

	@Override
	public List<RenderExtension<OpenGlBackend>> getRenderExtensions() {
		return Collections.emptyList();
	}

	public void drawToQuad(int texture, VertexBuffer buffer) {
		drawToQuad(texture, buffer, renderToQuadProgram);
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer, Program program) {
		program.use();

        gpuContext.bindFrameBuffer(0);
        gpuContext.viewPort(0,0, gpuContext.getCanvasWidth(), gpuContext.getCanvasHeight());
        gpuContext.disable(GlCap.DEPTH_TEST);

        gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, texture);

		buffer.draw();
	}

	public int drawLines(Program program) {
		return 0;
	}

	@Override
	public void batchLine(Vector3f from, Vector3f to) {
	}

	@Override
	public void batchTriangle(Vector3f a, Vector3f b, Vector3f c) {

	}

	private void setCurrentState(String newState) {
	}

	protected void finalize() throws Throwable {
		destroy();
	}

    @Override
	public DeferredRenderingBuffer getGBuffer() {
		return null;
	}

    @Override
    public void endFrame() {
        GPUProfiler.endFrame();
    }

	@Override
	public void drawAllLines(Consumer<Program> action) {

	}

	@Override
	public void startFrame() {
        GPUProfiler.startFrame();
	}

}
