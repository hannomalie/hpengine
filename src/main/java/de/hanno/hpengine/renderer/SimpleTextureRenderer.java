package de.hanno.hpengine.renderer;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.input.Input;
import de.hanno.hpengine.engine.model.DataChannels;
import de.hanno.hpengine.engine.model.QuadVertexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector3f;
import de.hanno.hpengine.renderer.constants.GlCap;
import de.hanno.hpengine.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.renderer.drawstrategy.GBuffer;
import de.hanno.hpengine.renderer.drawstrategy.SecondPassResult;
import de.hanno.hpengine.renderer.fps.FPSCounter;
import de.hanno.hpengine.renderer.light.LightFactory;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.scene.EnvironmentProbe;
import de.hanno.hpengine.scene.EnvironmentProbeFactory;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.shader.ProgramFactory;
import de.hanno.hpengine.texture.TextureFactory;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import de.hanno.hpengine.util.stopwatch.OpenGLStopWatch;
import de.hanno.hpengine.util.stopwatch.ProfilingTask;

import javax.vecmath.Vector2f;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static de.hanno.hpengine.log.ConsoleLogger.getLogger;

public class SimpleTextureRenderer implements Renderer {
	private static Logger LOGGER = getLogger();

	private volatile boolean initialized = false;

	public static EnumSet<DataChannels> RENDERTOQUAD = EnumSet.of(
			DataChannels.POSITION3,
			DataChannels.TEXCOORD);

	private OpenGLStopWatch glWatch;

	private static Program renderToQuadProgram;

	private ArrayList<VertexBuffer> sixDebugBuffers;

	private volatile AtomicInteger frameStarted = new AtomicInteger(0);
    private FPSCounter fpsCounter = new FPSCounter();

    public SimpleTextureRenderer() {
    }

	@Override
	public void init() {
		Renderer.super.init();

        if (!initialized) {
            setCurrentState("INITIALIZING");
            setupBuffers();
            ProgramFactory.init();
            TextureFactory.init();
            DeferredRenderer.exitOnGLError("After TextureFactory");
            try {
                setupShaders();
                setUpGBuffer();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Cannot init DeferredRenderer");
                System.exit(-1);
            }

            MaterialFactory.init();
            LightFactory.init();
            EnvironmentProbeFactory.init();

            initialized = true;
        }
	}

	private void setupBuffers() {

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
	}

    private void setUpGBuffer() {
        DeferredRenderer.exitOnGLError("Before setupGBuffer");

		OpenGLContext.getInstance().execute(() -> {
			OpenGLContext.getInstance().enable(GlCap.TEXTURE_CUBE_MAP_SEAMLESS);

			DeferredRenderer.exitOnGLError("setupGBuffer");
		});
	}
	
	private void setupShaders() throws Exception {
		DeferredRenderer.exitOnGLError("Before setupShaders");

		renderToQuadProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "simpletexture_fragment.glsl", false);

	}

	public void update(float seconds) {
	}


	public DrawResult draw(RenderState renderState) {
		GPUProfiler.start("Frame");
        DrawResult drawResult = new DrawResult(new FirstPassResult(), new SecondPassResult());
		GPUProfiler.end();
        drawToQuad(TextureFactory.getInstance().getDefaultTexture().getTextureID(), QuadVertexBuffer.getFullscreenBuffer());

        GPUProfiler.start("Waiting for driver");
		Display.update();
        GPUProfiler.end();
        return drawResult;
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
		drawToQuad(texture, QuadVertexBuffer.getFullscreenBuffer(), renderToQuadProgram);
	}

	public void drawToQuad(int texture, VertexBuffer buffer) {
		drawToQuad(texture, buffer, renderToQuadProgram);
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer, Program program) {
		program.use();

        OpenGLContext.getInstance().bindFrameBuffer(0);
        OpenGLContext.getInstance().viewPort(0,0, Engine.WINDOW_WIDTH, Engine.WINDOW_HEIGHT);
        OpenGLContext.getInstance().disable(GlCap.DEPTH_TEST);

		OpenGLContext.getInstance().bindTexture(0, GlTextureTarget.TEXTURE_2D, texture);

		buffer.draw();
	}

	private static long lastFrameTime = 0l;

	private void setLastFrameTime() {
		lastFrameTime = getTime();
        fpsCounter.update();
//		OpenGLContext.getInstance().execute(() -> {
			Display.setTitle(String.format("Render %03.0f fps | %03.0f ms - Update %03.0f fps | %03.0f ms",
                    fpsCounter.getFPS(), fpsCounter.getMsPerFrame(), Engine.getInstance().getFPSCounter().getFPS(), Engine.getInstance().getFPSCounter().getMsPerFrame()));
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
    public String endFrame() {
        GPUProfiler.endFrame();
        frameStarted.getAndDecrement();
		setLastFrameTime();
        return dumpTimings();
    }

	@Override
	public void startFrame() {
		frameStarted.getAndIncrement();
        GPUProfiler.startFrame();
	}

	@Override
	public boolean isFrameFinished() {
        return frameStarted.get() == 0;
	}

}
