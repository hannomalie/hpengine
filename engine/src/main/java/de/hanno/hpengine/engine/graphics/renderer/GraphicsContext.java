package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.PerFrameCommandProvider;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.constants.*;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.threads.TimeStepThread;
import de.hanno.hpengine.util.commandqueue.CommandQueue;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public interface GraphicsContext {
    Logger LOGGER = Logger.getLogger(GraphicsContext.class.getName());

    static void setInstance(GraphicsContext context) {
        GPUContextHelper.instance = context;
    }

    long getWindowHandle();

    int getCanvasWidth();

    int getCanvasHeight();

    void setCanvasWidth(int width);

    void setCanvasHeight(int height);

    long waitForGpuSync(long gpuCommandSync);

    boolean isSignaled(long gpuCommandSync);

    void createNewGPUFenceForReadState(RenderState currentReadState);

    void registerPerFrameCommand(PerFrameCommandProvider perFrameCommandProvider);

    class GPUContextHelper {
        static volatile GraphicsContext instance;
    }

    static GraphicsContext getInstance() {
        if(GPUContextHelper.instance == null || !GPUContextHelper.instance.isInitialized()) {
            throw new IllegalStateException("GraphicsContext context not initialized. Init an GraphicsContext first.");
        }
        return GPUContextHelper.instance;
    }

    static void initGpuContext() {
        Class<? extends GraphicsContext> gpuContextClass = Config.getInstance().getGpuContextClass();
        synchronized(gpuContextClass) {
            if(GPUContextHelper.instance == null) {
                try {
                    LOGGER.info("GraphicsContext is being initialized");
                    GraphicsContext context = gpuContextClass.newInstance();
                    context.init();
                    GraphicsContext.setInstance(context);
                    LOGGER.info("GraphicsContext is initialized");
                } catch (IllegalAccessException | InstantiationException e) {
                    LOGGER.severe("GraphicsContext class " + gpuContextClass.getCanonicalName() + " probably doesn't feature a public no args constructor");
                    e.printStackTrace();
                }
            }
        }
    }

    static void exitOnGLError(String errorMessage) {
        if (!Renderer.CHECKERRORS) {
            return;
        }

        int errorValue = GL11.glGetError();

        if (errorValue != GL11.GL_NO_ERROR) {
            String errorString = GLU.gluErrorString(errorValue);
            System.err.println("ERROR - " + errorMessage + ": " + errorString);

            new RuntimeException("").printStackTrace();
            System.exit(-1);
        }
    }

    boolean isError();

    void init();

    void update(float seconds);

    void enable(GlCap cap);

    void disable(GlCap cap);

    void activeTexture(int textureUnitIndex);

    void bindTexture(GlTextureTarget target, int textureId);

    void bindTexture(int textureUnitIndex, GlTextureTarget target, int textureId);

    void bindTextures(IntBuffer textureIds);

    void bindTextures(int count, IntBuffer textureIds);

    void bindTextures(int firstUnit, int count, IntBuffer textureIds);

    void viewPort(int x, int y, int width, int height);

    void clearColorBuffer();

    void clearDepthBuffer();

    void clearDepthAndColorBuffer();

    void bindFrameBuffer(int frameBuffer);

    void depthMask(boolean flag);

    void depthFunc(GlDepthFunc func);

    void readBuffer(int colorAttachmentIndex);

    void blendEquation(BlendMode mode);

    void blendFunc(BlendMode.Factor sfactor, BlendMode.Factor dfactor);

    void cullFace(CullMode mode);

    void clearColor(float r, float g, float b, float a);

    void bindImageTexture(int unit, int textureId, int level, boolean layered, int layer, int access, int internalFormat);

    int genTextures();

    int getAvailableVRAM();

    int getAvailableTotalVRAM();

    int getDedicatedVRAM();

    int getEvictedVRAM();

    int getEvictionCount();

    boolean isInitialized();

    void execute(Runnable runnable);

    void execute(Runnable runnable, boolean andBlock);

    <RETURN_TYPE> RETURN_TYPE calculate(Callable<RETURN_TYPE> callable);

    <RETURN_TYPE> CompletableFuture<RETURN_TYPE> execute(FutureCallable<RETURN_TYPE> command);

    long blockUntilEmpty();

    TimeStepThread getDrawThread();

    int createProgramId();

    int getMaxTextureUnits();

    CommandQueue getCommandQueue();

    void benchmark(Runnable runnable);

    void destroy();

}
