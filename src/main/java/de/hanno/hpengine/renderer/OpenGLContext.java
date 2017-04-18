package de.hanno.hpengine.renderer;

import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.CanvasWrapper;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.TimeStepThread;
import de.hanno.hpengine.engine.graphics.query.GLTimerQuery;
import de.hanno.hpengine.renderer.constants.*;
import de.hanno.hpengine.util.commandqueue.CommandQueue;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;
import org.lwjgl.opengl.DisplayMode;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static de.hanno.hpengine.renderer.constants.GlCap.CULL_FACE;
import static de.hanno.hpengine.renderer.constants.GlCap.DEPTH_TEST;

public final class OpenGLContext implements GraphicsContext {
    private static final Logger LOGGER = Logger.getLogger(OpenGLContext.class.getName());

    private static final int MAX_WORKITEMS = 1000;
    private static long OPENGL_THREAD_ID = -1;

    public static String OPENGL_THREAD_NAME = "OpenGLContext";

    private static ExecutorService executorService = Executors.newSingleThreadExecutor();


    private int canvasWidth = Config.getInstance().getWidth();
    private int canvasHeight = Config.getInstance().getHeight();

    private TimeStepThread openGLThread;
    private boolean attached;

    private CommandQueue commandQueue = new CommandQueue();
    private volatile boolean initialized = false;
    public volatile boolean errorOccured = false;
    private int maxTextureUnits;

    protected OpenGLContext() {
    }

    @Override
    public boolean isAttachedTo(CanvasWrapper canvas) {
        return Display.getParent() != null && Display.getParent().equals(canvas);
    }

    @Override
    public int getCanvasWidth() {
        return canvasWidth;
    }

    @Override
    public int getCanvasHeight() {
        return canvasHeight;
    }

    @Override
    public void setCanvasWidth(int width) {
        canvasWidth = width;
    }

    @Override
    public void setCanvasHeight(int height) {
        canvasHeight = height;
    }

    @Override
    public boolean isError() {
        return GraphicsContext.getInstance().calculate(() -> GL11.glGetError() != GL11.GL_NO_ERROR);
    }

    private final void privateInit() throws LWJGLException {
        PixelFormat pixelFormat = new PixelFormat();
        ContextAttribs contextAttributes = new ContextAttribs(4, 5)
				.withProfileCompatibility(true)
                .withForwardCompatible(true)
//                .withProfileCore(true)
                .withDebug(true)
                ;

        LOGGER.info("OpenGLContext before setDisplayMode");
        Display.setDisplayMode(new DisplayMode(Config.getInstance().getWidth(), Config.getInstance().getHeight()));
        LOGGER.info("OpenGLContext after setDisplayMode");
        Display.setTitle("HPEngine");
        Display.create(pixelFormat, contextAttributes);
        Display.setVSyncEnabled(Config.getInstance().isVsync());

//        ContextCapabilities capabilities = GLContext.getCapabilities();
//        System.out.println("######## Sparse texutre ext available:");
//        System.out.println(capabilities.GL_ARB_sparse_texture);
//        System.out.println(capabilities.GL_EXT_direct_state_access);
//        System.out.println(GL11.glGetString(GL11.GL_EXTENSIONS));

        try {
            Keyboard.create();
        } catch (LWJGLException e) {
            e.printStackTrace();
        }
        this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        Display.setResizable(false);
        KHRDebugCallback.Handler handler = new KHRDebugCallback.Handler() {
            @Override
            public void handleMessage(int source, int type, int id, int severity, String message) {
                if(severity == KHRDebug.GL_DEBUG_SEVERITY_HIGH) {
                    Logger.getGlobal().severe(message);
                    errorOccured = true;
                    System.out.println("message = " + message);
                    new RuntimeException().printStackTrace();
                }
            }
        };
        GL43.glDebugMessageCallback(new KHRDebugCallback(handler));
        Keyboard.create();

        enable(DEPTH_TEST);
        enable(CULL_FACE);

        // Map the internal OpenGL coordinate system to the entire screen
        viewPort(0, 0, Config.getInstance().getWidth(), Config.getInstance().getHeight());
        maxTextureUnits = (GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS));

        initialized = true;
        LOGGER.info("OpenGLContext initialized");
    }

    @Override
    public void update(float seconds) {
        try {
            commandQueue.executeCommands();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean attach(CanvasWrapper canvasWrapper) {
        try {
            Display.setParent(canvasWrapper.getCanvas());
            Engine.getInstance().setSetTitleRunnable(canvasWrapper.getSetTitleRunnable());
            attached = true;
        } catch (LWJGLException e) {
            attached = false;
            e.printStackTrace();
        }
        return attached;
    }
    @Override
    public boolean detach() {
        try {
            Display.setParent(null);
            Engine.getInstance().setSetTitleRunnable(() -> {});
            attached = false;
        } catch (LWJGLException e) {
            e.printStackTrace();
        }
        return !attached;
    }
    @Override
    public void attachOrDetach(CanvasWrapper canvasWrapper) {
        if(attached) {
            detach();
        } else {
            attach(canvasWrapper);
        }
    }

    @Override
    public void enable(GlCap cap) {
        cap.enable();
    }
    @Override
    public void disable(GlCap cap) {
        cap.disable();
    }

    private int activeTexture = -1;
    @Override
    public void activeTexture(int textureUnitIndex) {
        int textureIndexGLInt = getOpenGLTextureUnitValue(textureUnitIndex);
//        TODO: Use this
//        if(activeTexture != textureIndexGLInt)
        {
            execute(() -> GL13.glActiveTexture(textureIndexGLInt));
        }
    }
    private int getCleanedTextureUnitValue(int textureUnit) {
        return textureUnit - GL13.GL_TEXTURE0;
    }
    private int getOpenGLTextureUnitValue(int textureUnitIndex) {
        return GL13.GL_TEXTURE0 + textureUnitIndex;
    }

    private HashMap<Integer, Integer> textureBindings = new HashMap<>();
    @Override
    public void bindTexture(GlTextureTarget target, int textureId) {
        GraphicsContext.getInstance().execute(() -> {
            GL11.glBindTexture(target.glTarget, textureId);
            textureBindings.put(getCleanedTextureUnitValue(activeTexture), textureId);
        });
    }

    private void printTextureBindings() {
        for(Map.Entry<Integer, Integer> entry : textureBindings.entrySet()) {
            LOGGER.info("Slot " + entry.getKey() + " -> Texture " + entry.getValue());
        }
    }

    @Override
    public void bindTexture(int textureUnitIndex, GlTextureTarget target, int textureId) {
        GraphicsContext.getInstance().execute(() -> {
        // TODO: Use when no bypassing calls to bindtexture any more
//        if(!textureBindings.containsKey(textureUnitIndex) ||
//           (textureBindings.containsKey(textureUnitIndex) && textureId != textureBindings.get(textureUnitIndex))) {
            activeTexture(textureUnitIndex);
            GL11.glBindTexture(target.glTarget, textureId);
            textureBindings.put(textureUnitIndex, textureId);
        });
    }

    @Override
    public void bindTextures(IntBuffer textureIds) {
        GraphicsContext.getInstance().execute(() -> {
            GL44.glBindTextures(0, textureIds.capacity(), textureIds);
        });
    }
    @Override
    public void bindTextures(int count, IntBuffer textureIds) {
        GraphicsContext.getInstance().execute(() -> {
            GL44.glBindTextures(0, count, textureIds);
        });
    }
    @Override
    public void bindTextures(int firstUnit, int count, IntBuffer textureIds) {
        GraphicsContext.getInstance().execute(() -> {
            GL44.glBindTextures(firstUnit, count, textureIds);
        });
    }

    @Override
    public void viewPort(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    @Override
    public void clearColorBuffer() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    @Override
    public void clearDepthBuffer() {
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    @Override
    public void clearDepthAndColorBuffer() {
        clearDepthBuffer();
        clearColorBuffer();
    }

    private int currentFrameBuffer = -1;
    @Override
    public void bindFrameBuffer(int frameBuffer) {
        if(currentFrameBuffer != frameBuffer) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBuffer);
            currentFrameBuffer = frameBuffer;
        }
    }

    private boolean depthMask = false;
    @Override
    public void depthMask(boolean flag) {
        if(depthMask != flag) {
            GL11.glDepthMask(flag);
            depthMask = flag;
        }
    }
    @Override
    public void depthFunc(GlDepthFunc func) {
        GL11.glDepthFunc(func.glFunc);
    }

    int currentReadBuffer = -1;
    @Override
    public void readBuffer(int colorAttachmentIndex) {
        int colorAttachment = GL30.GL_COLOR_ATTACHMENT0 + colorAttachmentIndex;
        if(currentReadBuffer != colorAttachment) {
            GL11.glReadBuffer(colorAttachment);
        }
    }

    private BlendMode currentBlendMode = BlendMode.FUNC_ADD;
    @Override
    public void blendEquation(BlendMode mode) {
        GL14.glBlendEquation(mode.mode);
    }

    @Override
    public void blendFunc(BlendMode.Factor sfactor, BlendMode.Factor dfactor) {
        GL11.glBlendFunc(sfactor.glFactor, dfactor.glFactor);
    }

    @Override
    public void cullFace(CullMode mode) {
        GL11.glCullFace(mode.glMode);
    }

    @Override
    public void clearColor(float r, float g, float b, float a) {
        execute(() -> {
            GL11.glClearColor(r, g, b, a);
        });
    }

    @Override
    public void bindImageTexture(int unit, int textureId, int level, boolean layered, int layer, int access, int internalFormat) {
        // TODO: create access enum
        GL42.glBindImageTexture(unit, textureId, level, layered, layer, access, internalFormat);
    }

    @Override
    public int genTextures() {
        return calculate(() -> GL11.glGenTextures());
    }

    @Override
    public int getAvailableVRAM() {
        return calculate(() -> GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX));
    }
    @Override
    public int getAvailableTotalVRAM() {
        return calculate(() -> GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX));
    }
    @Override
    public int getDedicatedVRAM() {
        return calculate(() -> GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX));
    }
    @Override
    public int getEvictedVRAM() {
        return calculate(() -> GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_EVICTED_MEMORY_NVX));
    }
    @Override
    public int getEvictionCount() {
        return calculate(() -> GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX));
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void execute(Runnable runnable) {
        execute(runnable, true);
    }
    @Override
    public void execute(Runnable runnable, boolean andBlock) {
        if(isOpenGLThread()) {
            runnable.run();
            return;
        }

        if(andBlock) {
            commandQueue.execute(runnable, andBlock);
        } else {
            calculate(() -> {
                runnable.run();
                return null;
            });
        }
    }

    @Override
    public <RETURN_TYPE> RETURN_TYPE calculate(Callable<RETURN_TYPE> callable) {
        try {
            return execute(callable).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public <RETURN_TYPE> CompletableFuture<RETURN_TYPE> execute(Callable<RETURN_TYPE> callable) {

        if(isOpenGLThread()) {
            try {
                CompletableFuture<RETURN_TYPE> result = new CompletableFuture();
                try {
                    result.complete(callable.call());
                } catch(Exception e) {
                    result.completeExceptionally(e);
                }
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            CompletableFuture future = commandQueue.addCommand(new FutureCallable() {
                                                                   @Override
                                                                   public RETURN_TYPE execute() throws Exception {
                                                                       return callable.call();
                                                                   }
                                                               }
            );
            return future;
        }
    }

    @Override
    public long blockUntilEmpty() {
        long start = System.currentTimeMillis();
        while(commandQueue.size() > 0) {
            try {
                Thread.sleep(0, 100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return System.currentTimeMillis() - start;
    }

    @Override
    public TimeStepThread getDrawThread() {
        return openGLThread;
    }

    @Override
    public int createProgramId() {
        return calculate(() -> {
            return GL20.glCreateProgram();
        });
    }

    @Override
    public int getMaxTextureUnits() {
        return maxTextureUnits;
    }

    @Override
    public CommandQueue getCommandQueue() {
        return commandQueue;
    }

    @Override
    public void benchmark(Runnable runnable) {
        GLTimerQuery.getInstance().begin();
        runnable.run();
        GLTimerQuery.getInstance().end();
        LOGGER.info(GLTimerQuery.getInstance().getResult().toString());
    }


    @Override
    public void destroy() {
        openGLThread.stopRequested = true;
        try {
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void init(CanvasWrapper canvasWrapper) {
        attach(canvasWrapper);
        this.openGLThread = new TimeStepThread(OpenGLContext.OPENGL_THREAD_NAME, 0.0f) {

            @Override
            public void update(float seconds) {
                if (!OpenGLContext.this.isInitialized()) {
                    Thread.currentThread().setName(OpenGLContext.OPENGL_THREAD_NAME);
                    OpenGLContext.OPENGL_THREAD_ID = Thread.currentThread().getId();
                    System.out.println("OPENGL_THREAD_ID is " + OpenGLContext.OPENGL_THREAD_ID);
                    try {
                        try {
                            OpenGLContext.this.privateInit();
                        } catch (Exception e) {
                            OpenGLContext.LOGGER.severe("Exception during privateInit");
                            e.printStackTrace();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(-1);
                    }
                } else {
                    try {
                        OpenGLContext.this.update(seconds);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        OpenGLContext.executorService.submit(this.openGLThread);
        System.out.println("OpenGLContext thread submitted with id " + OpenGLContext.OPENGL_THREAD_ID);
        waitForInitialization(this);
    }

    static void waitForInitialization(GraphicsContext context) {
        OpenGLContext.LOGGER.info("Waiting for OpenGLContext initialization");
        while(!context.isInitialized()) {
            OpenGLContext.LOGGER.info("Waiting for OpenGLContext initialization...");
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        OpenGLContext.LOGGER.info("OpenGLContext ready");
    }

    static boolean isOpenGLThread() {
        if(OpenGLContext.OPENGL_THREAD_ID == -1) throw new IllegalStateException("OpenGLThread id is -1, initialization failed!");
        return Thread.currentThread().getId() == OpenGLContext.OPENGL_THREAD_ID;
    }
}
