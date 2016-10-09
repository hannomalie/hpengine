package renderer;

import config.Config;
import engine.TimeStepThread;
import engine.graphics.query.GLTimerQuery;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.util.glu.GLU;
import renderer.constants.*;
import util.commandqueue.CommandQueue;
import util.commandqueue.FutureCallable;

import java.awt.*;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static renderer.constants.GlCap.CULL_FACE;
import static renderer.constants.GlCap.DEPTH_TEST;

public final class OpenGLContext {
    private static final Logger LOGGER = Logger.getLogger(OpenGLContext.class.getName());

    private static final int MAX_WORKITEMS = 1000;

    public static String OPENGL_THREAD_NAME = "OpenGLContext";

    private static ExecutorService executorService = Executors.newSingleThreadExecutor();

    private TimeStepThread openGLThread;
    private boolean attached;

    private static volatile OpenGLContext instance;
    private CommandQueue commandQueue = new CommandQueue();
    private volatile boolean initialized = false;
    public volatile boolean errorOccured = false;
    private int maxTextureUnits;

    public static OpenGLContext getInstance() {
        if(instance == null) {
            synchronized(OpenGLContext.class) {
                if(instance == null) {
                    try {
                        LOGGER.info("OpenGLContext is being initialized");
                        OpenGLContext.init();
                        LOGGER.info("OpenGLContext is initialized");
                    } catch (LWJGLException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else if(!instance.isInitialized()) {
            throw new IllegalStateException("OpenGL context not initialized. Init an OpenGLContext first.");
        }
        return instance;
    }

    private OpenGLContext() {
    }

    public static void exitOnGLError(String errorMessage) {
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

    public static void init() throws LWJGLException {
        OpenGLContext context = new OpenGLContext();
        instance = context;
        context.openGLThread = new TimeStepThread(OPENGL_THREAD_NAME, 0.0f) {

            @Override
            public void update(float seconds) {
                if (!context.isInitialized()) {
                    Thread.currentThread().setName(OPENGL_THREAD_NAME);
                    try {
                        try {
                            context.privateInit();
                        } catch (Exception e) {
                            LOGGER.severe("Exception during privateInit");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(-1);
                    }
                } else {
                    instance.update(seconds);
                }
            }
        };
        executorService.submit(context.openGLThread);
        System.out.println("OpenGLContext thread submitted");
        waitForInitialization(context);
    }

    public static final void waitForInitialization(OpenGLContext context) {
        LOGGER.info("Waiting for OpenGLContext initialization");
        while(!context.isInitialized()) {
            LOGGER.info("Waiting for OpenGLContext initialization...");
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        LOGGER.info("OpenGLContext ready");
    }

    private final void privateInit() throws LWJGLException {
        PixelFormat pixelFormat = new PixelFormat();
        ContextAttribs contextAttributes = new ContextAttribs(4, 5)
				.withProfileCompatibility(true)
                .withForwardCompatible(true)
//                .withProfileCore(true)
//                .withDebug(true)
                ;

        LOGGER.info("OpenGLContext before setDisplayMode");
        Display.setDisplayMode(new DisplayMode(Config.WIDTH, Config.HEIGHT));
        LOGGER.info("OpenGLContext after setDisplayMode");
        Display.setTitle("DeferredRenderer");
        Display.create(pixelFormat, contextAttributes);
        Display.setVSyncEnabled(false);

        ContextCapabilities capabilities = GLContext.getCapabilities();
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
                    new RuntimeException().printStackTrace();
                }
            }
        };
        GL43.glDebugMessageCallback(new KHRDebugCallback(handler));
        Keyboard.create();

        enable(DEPTH_TEST);
        enable(CULL_FACE);

        // Map the internal OpenGL coordinate system to the entire screen
        viewPort(0, 0, Config.WIDTH, Config.HEIGHT);
        maxTextureUnits = (GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS));

        initialized = true;
        LOGGER.info("OpenGLContext initialized");
    }

    public void update(float seconds) {
        try {
            commandQueue.executeCommands();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void attach(Canvas canvas) throws LWJGLException {
        Display.setParent(canvas);
        attached = true;
    }
    public void detach() throws LWJGLException {
        Display.setParent(null);
        attached = false;
    }
    public void attachOrDetach(Canvas canvas) throws LWJGLException {
        if(attached) {
            detach();
        } else {
            attach(canvas);
        }
    }

    public void enable(GlCap cap) {
        cap.enable();
    }
    public void disable(GlCap cap) {
        cap.disable();
    }

    private int activeTexture = -1;
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
    public void bindTexture(GlTextureTarget target, int textureId) {
        OpenGLContext.getInstance().execute(() -> {
            GL11.glBindTexture(target.glTarget, textureId);
            textureBindings.put(getCleanedTextureUnitValue(activeTexture), textureId);
//            printTextureBindings();
        });
    }

    private void printTextureBindings() {
        for(Map.Entry<Integer, Integer> entry : textureBindings.entrySet()) {
            LOGGER.info("Slot " + entry.getKey() + " -> Texture " + entry.getValue());
        }
    }

    public void bindTexture(int textureUnitIndex, GlTextureTarget target, int textureId) {
        OpenGLContext.getInstance().execute(() -> {
        // TODO: Use when no bypassing calls to bindtexture any more
//        if(!textureBindings.containsKey(textureUnitIndex) ||
//           (textureBindings.containsKey(textureUnitIndex) && textureId != textureBindings.get(textureUnitIndex))) {
            activeTexture(textureUnitIndex);
            GL11.glBindTexture(target.glTarget, textureId);
            textureBindings.put(textureUnitIndex, textureId);
        });
    }

    public void bindTextures(IntBuffer textureIds) {
        OpenGLContext.getInstance().execute(() -> {
            GL44.glBindTextures(0, textureIds.capacity(), textureIds);
        });
    }
    public void bindTextures(int count, IntBuffer textureIds) {
        OpenGLContext.getInstance().execute(() -> {
            GL44.glBindTextures(0, count, textureIds);
        });
    }
    public void bindTextures(int firstUnit, int count, IntBuffer textureIds) {
        OpenGLContext.getInstance().execute(() -> {
            GL44.glBindTextures(firstUnit, count, textureIds);
        });
    }

    public void viewPort(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    public void clearColorBuffer() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    public void clearDepthBuffer() {
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    public void clearDepthAndColorBuffer() {
        clearDepthBuffer();
        clearColorBuffer();
    }

    private int currentFrameBuffer = -1;
    public void bindFrameBuffer(int frameBuffer) {
        if(currentFrameBuffer != frameBuffer) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBuffer);
            currentFrameBuffer = frameBuffer;
        }
    }

    private boolean depthMask = false;
    public void depthMask(boolean flag) {
        if(depthMask != flag) {
            GL11.glDepthMask(flag);
            depthMask = flag;
        }
    }
    public void depthFunc(GlDepthFunc func) {
        GL11.glDepthFunc(func.glFunc);
    }

    int currentReadBuffer = -1;
    public void readBuffer(int colorAttachmentIndex) {
        int colorAttachment = GL30.GL_COLOR_ATTACHMENT0 + colorAttachmentIndex;
        if(currentReadBuffer != colorAttachment) {
            GL11.glReadBuffer(colorAttachment);
        }
    }

    private BlendMode currentBlendMode = BlendMode.FUNC_ADD;
    public void blendEquation(BlendMode mode) {
        GL14.glBlendEquation(mode.mode);
    }

    public void blendFunc(BlendMode.Factor sfactor, BlendMode.Factor dfactor) {
        GL11.glBlendFunc(sfactor.glFactor, dfactor.glFactor);
    }

    public void cullFace(CullMode mode) {
        GL11.glCullFace(mode.glMode);
    }

    public void clearColor(float r, float g, float b, float a) {
        execute(() -> {
            GL11.glClearColor(r, g, b, a);
        });
    }

    public void bindImageTexture(int unit, int textureId, int level, boolean layered, int layer, int access, int internalFormat) {
        // TODO: create access enum
        GL42.glBindImageTexture(unit, textureId, level, layered, layer, access, internalFormat);
    }

    public int genTextures() {
        return calculate(() -> GL11.glGenTextures());
    }

    public int getAvailableVRAM() {
        return calculate(() -> GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX));
    }
    public int getAvailableTotalVRAM() {
        return calculate(() -> GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX));
    }
    public int getDedicatedVRAM() {
        return calculate(() -> GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX));
    }
    public int getEvictedVRAM() {
        return calculate(() -> GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_EVICTED_MEMORY_NVX));
    }
    public int getEvictionCount() {
        return calculate(() -> GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX));
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void execute(Runnable runnable) {
        execute(runnable, true);
    }
    public Exception execute(Runnable runnable, boolean andBlock) {
        if(util.Util.isOpenGLThread()) {
            runnable.run();
            return null;
        }
        CompletableFuture<Object> future = execute(() -> {
            try {
                runnable.run();
            } catch(Exception e) {
                LOGGER.severe(e.toString());
                return e;
            }
            return null;
        });

        if(andBlock) {
            future.join();
        }
        return null;
    }

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

    public <RETURN_TYPE> CompletableFuture<RETURN_TYPE> execute(Callable<RETURN_TYPE> callable) {

        if(util.Util.isOpenGLThread()) {
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

    public TimeStepThread getDrawThread() {
        return openGLThread;
    }

    public int createProgramId() {
        return calculate(() -> {
            return GL20.glCreateProgram();
        });
    }

    public int getMaxTextureUnits() {
        return maxTextureUnits;
    }

    public CommandQueue getCommandQueue() {
        return commandQueue;
    }

    public void benchmark(Runnable runnable) {
        GLTimerQuery.getInstance().begin();
        runnable.run();
        GLTimerQuery.getInstance().end();
        LOGGER.info(GLTimerQuery.getInstance().getResult().toString());
    }

    public void destroy() {
        openGLThread.stopRequested = true;
        try {
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void executeNow(Runnable runnable, boolean andBlock) {
        CompletableFuture result = new CompletableFuture();
        if(util.Util.isOpenGLThread()) {
            try {
                try {
                    runnable.run();
                    result.complete(true);
                } catch(Exception e) {
                    result.completeExceptionally(e);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            commandQueue.getWorkQueue().addFirst(new FutureCallable() {
                                                       @Override
                                                       public Object execute() throws Exception {
                                                           try {
                                                               runnable.run();
                                                               result.complete(true);
                                                           } catch(Exception e) {
                                                               result.completeExceptionally(e);
                                                           }
                                                           return true;
                                                       }
                                                   });
        }
        if(andBlock) {
            try {
                result.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
    }
}
