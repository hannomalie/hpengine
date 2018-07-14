package de.hanno.hpengine.engine.graphics;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.GLU;
import de.hanno.hpengine.engine.graphics.renderer.constants.*;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.model.texture.ITexture;
import de.hanno.hpengine.engine.threads.TimeStepThread;
import de.hanno.hpengine.util.commandqueue.CommandQueue;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public interface GpuContext {
    boolean CHECKERRORS = false;

    Logger LOGGER = Logger.getLogger(GpuContext.class.getName());

    RenderTarget getFrontBuffer();

    long getWindowHandle();

    int getCanvasWidth();

    int getCanvasHeight();

    void setCanvasWidth(int width);

    void setCanvasHeight(int height);

    void createNewGPUFenceForReadState(RenderState currentReadState);

    void registerPerFrameCommand(PerFrameCommandProvider perFrameCommandProvider);

    static GpuContext create() {
        Class<? extends GpuContext> gpuContextClass = Config.getInstance().getGpuContextClass();
        try {
            LOGGER.info("GpuContext is being initialized");
            GpuContext context = gpuContextClass.newInstance();
            return context;
        } catch (IllegalAccessException | InstantiationException e) {
            LOGGER.severe("GpuContext class " + gpuContextClass.getCanonicalName() + " probably doesn't feature a public no args constructor");
            e.printStackTrace();
        }
        return null;
    }

    static void exitOnGLError(String errorMessage) {
        if (!CHECKERRORS) {
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

    void update(float seconds);

    void enable(GlCap cap);

    void disable(GlCap cap);

    void activeTexture(int textureUnitIndex);

    void bindTexture(int textureUnitIndex, GlTextureTarget target, int textureId);

    default void bindTexture(GlTextureTarget target, int textureId) {
        bindTexture(0, target, textureId);
    }

    default void bindTexture(int textureUnitIndex, ITexture<?> texture) {
        bindTexture(textureUnitIndex, texture.getTarget(), texture.getTextureId());
        texture.setUsedNow();
    }

    void bindTextures(IntBuffer textureIds);

    void bindTextures(int count, IntBuffer textureIds);

    void bindTextures(int firstUnit, int count, IntBuffer textureIds);

    default void unbindTexture(int textureUnitIndex, ITexture texture) {
        bindTexture(textureUnitIndex, texture.getTarget(), 0);
    }


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

    TimeStepThread getGpuThread();

    int createProgramId();

    int getMaxTextureUnits();

    CommandQueue getCommandQueue();

    void benchmark(Runnable runnable);

    void destroy();

    VertexBuffer getFullscreenBuffer();
    VertexBuffer getDebugBuffer();


    int genFrameBuffer();

    void clearCubeMap(int i, int textureFormat);

    void clearCubeMapInCubeMapArray(int textureID, int internalFormat, int width, int height, int cubeMapIndex);

    void register(RenderTarget target);

    List<RenderTarget> getRegisteredRenderTargets();
}
