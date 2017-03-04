package de.hanno.hpengine.renderer;

import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.TimeStepThread;
import de.hanno.hpengine.renderer.constants.*;
import de.hanno.hpengine.util.commandqueue.CommandQueue;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.awt.*;
import java.nio.IntBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public interface GraphicsContext {
    Logger LOGGER = Logger.getLogger(GraphicsContext.class.getName());

    static void setInstance(GraphicsContext context) {
        GPUContextHelper.instance = context;
    }

    class GPUContextHelper {
        static volatile GraphicsContext instance;
    }

    static GraphicsContext getInstance() {
        if(GPUContextHelper.instance == null) {
            initGpuContext();
        } else if(!GPUContextHelper.instance.isInitialized()) {
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

            if (Display.isCreated()) Display.destroy();
            System.exit(-1);
        }
    }

    boolean isError();

    void init();

    void update(float seconds);

    void attach(Canvas canvas) throws LWJGLException;

    void detach() throws LWJGLException;

    void attachOrDetach(Canvas canvas) throws LWJGLException;

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

    <RETURN_TYPE> CompletableFuture<RETURN_TYPE> execute(Callable<RETURN_TYPE> callable);

    long blockUntilEmpty();

    TimeStepThread getDrawThread();

    int createProgramId();

    int getMaxTextureUnits();

    CommandQueue getCommandQueue();

    void benchmark(Runnable runnable);

    void destroy();
}
