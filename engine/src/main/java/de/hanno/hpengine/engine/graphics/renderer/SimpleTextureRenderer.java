package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.GBuffer;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.QuadVertexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;
import de.hanno.hpengine.log.ConsoleLogger;
import de.hanno.hpengine.util.fps.FPSCounter;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import de.hanno.hpengine.util.stopwatch.OpenGLStopWatch;
import de.hanno.hpengine.util.stopwatch.ProfilingTask;
import org.joml.Vector3f;

import javax.vecmath.Vector2f;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;

public class SimpleTextureRenderer implements Renderer {
	private static Logger LOGGER = ConsoleLogger.getLogger();

	private volatile boolean initialized = false;

	public static EnumSet<DataChannels> RENDERTOQUAD = EnumSet.of(
			DataChannels.POSITION3,
			DataChannels.TEXCOORD);

	private OpenGLStopWatch glWatch;

	private static Program renderToQuadProgram;

	private ArrayList<VertexBuffer> sixDebugBuffers;

    private FPSCounter fpsCounter = new FPSCounter();
	private Engine engine;

	public SimpleTextureRenderer() {
    }

	@Override
	public void init(Engine engine) {
		Renderer.super.init(engine);
		this.engine = engine;

        if (!initialized) {
            setCurrentState("INITIALIZING");
            setupBuffers(engine.getGpuContext());
            GpuContext.exitOnGLError("After TextureManager");
            try {
                setupShaders();
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

        engine.getGpuContext().execute(() -> {
            engine.getGpuContext().enable(GlCap.TEXTURE_CUBE_MAP_SEAMLESS);

			GpuContext.exitOnGLError("setupGBuffer");
		});
	}
	
	private void setupShaders() throws Exception {
		GpuContext.exitOnGLError("Before setupShaders");

        renderToQuadProgram = engine.getProgramManager().getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "passthrough_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "simpletexture_fragment.glsl")), new Defines());

	}

	public void update(Engine engine, float seconds) {
	}


	@Override
	public void draw(DrawResult result, RenderState renderState) {
		GPUProfiler.start("Frame");
		drawToQuad(engine.getTextureManager().getDefaultTexture().getTextureID(), engine.getGpuContext().getFullscreenBuffer());
		GPUProfiler.end();

        GPUProfiler.start("Waiting for driver");
		glfwPollEvents();
        glfwSwapBuffers(engine.getGpuContext().getWindowHandle());
        GPUProfiler.end();
	}

	private String dumpTimings() {
		ProfilingTask tp;
        StringBuilder builder = new StringBuilder();
		while((tp = GPUProfiler.getFrameResults()) != null){
            tp.dump(builder); //Dumps the frame to System.out.
		}
        GPUProfiler.dumpAverages();
        return builder.toString();
	}

	@Override
	public void drawToQuad(int texture) {
		drawToQuad(texture, engine.getGpuContext().getFullscreenBuffer(), renderToQuadProgram);
	}

	public void drawToQuad(int texture, VertexBuffer buffer) {
		drawToQuad(texture, buffer, renderToQuadProgram);
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer, Program program) {
		program.use();

        engine.getGpuContext().bindFrameBuffer(0);
        engine.getGpuContext().viewPort(0,0, engine.getGpuContext().getCanvasWidth(), engine.getGpuContext().getCanvasHeight());
        engine.getGpuContext().disable(GlCap.DEPTH_TEST);

        engine.getGpuContext().bindTexture(0, GlTextureTarget.TEXTURE_2D, texture);

		buffer.draw();
	}

	private static long lastFrameTime = 0l;

	private void setLastFrameTime() {
		lastFrameTime = getTime();
        fpsCounter.update();
//		OpenGLContext.getInstance().execute(() -> {
//			Display.setTitle(String.format("Render %03.0f fps | %03.0f ms - Update %03.0f fps | %03.0f ms",
//                    fpsCounter.getFPS(), fpsCounter.getMsPerFrame(), engine.getFPSCounter().getFPS(), engine.getFPSCounter().getMsPerFrame()));
//		});
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

	@Override
	public boolean isInitialized() {
		return initialized;
	}

	public int drawLines(Program program) {
		return 0;
	}

	@Override
	public void batchLine(Vector3f from, Vector3f to) {
	}

	@Override
	public void executeRenderProbeCommands(RenderState extract) {
	}

	@Override
	public void addRenderProbeCommand(EnvironmentProbe probe) {
	}
	@Override
	public void addRenderProbeCommand(EnvironmentProbe probe, boolean urgent) {
	}

    @Override
    public FPSCounter getFPSCounter() {
        return fpsCounter;
    }

	private void setCurrentState(String newState) {
	}

	protected void finalize() throws Throwable {
		destroy();
	}

    @Override
	public GBuffer getGBuffer() {
		return null;
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
