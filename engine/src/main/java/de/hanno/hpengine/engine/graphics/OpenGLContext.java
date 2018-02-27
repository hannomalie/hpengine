package de.hanno.hpengine.engine.graphics;

import de.hanno.hpengine.engine.PerFrameCommandProvider;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.constants.*;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.model.QuadVertexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.threads.TimeStepThread;
import de.hanno.hpengine.util.commandqueue.CommandQueue;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import org.lwjgl.LWJGLException;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWWindowCloseCallbackI;
import org.lwjgl.opengl.*;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL32.*;

public final class OpenGLContext implements GpuContext {
    private static final Logger LOGGER = Logger.getLogger(OpenGLContext.class.getName());

    private static long OPENGL_THREAD_ID = -1;

    public static String OPENGL_THREAD_NAME = "OpenGLContext";

    private static ExecutorService executorService = Executors.newSingleThreadExecutor();
    private RenderTarget frontBuffer;


    public RenderTarget createFrontBuffer() {
        RenderTarget frontBuffer = new RenderTarget(this) {
            @Override
            public int getWidth() {
                return getCanvasWidth();
            }

            @Override
            public int getHeight() {
                return getCanvasHeight();
            }

            @Override
            public void use(boolean clear) {
                super.use(false);
            }
        };
        frontBuffer.framebufferLocation = 0;
        return frontBuffer;
    }


    private int canvasWidth = Config.getInstance().getWidth();
    private int canvasHeight = Config.getInstance().getHeight();

    private TimeStepThread openGLThread;

    private CommandQueue commandQueue = new CommandQueue();
    private volatile boolean initialized = false;
    public volatile boolean errorOccured = false;
    private int maxTextureUnits;
    private List<PerFrameCommandProvider> perFrameCommandProviders = new CopyOnWriteArrayList<>();
    private GLFWErrorCallback errorCallback;
    private long window;
    // Don't remove these strong references
    private GLFWFramebufferSizeCallback framebufferSizeCallback;
    private GLFWWindowCloseCallbackI closeCallback = l -> System.exit(0);

    protected OpenGLContext() {
        init();
        fullscreenBuffer = new QuadVertexBuffer(this, true);
        debugBuffer = new QuadVertexBuffer(this, false);

        fullscreenBuffer.upload();
        debugBuffer.upload();
    }

    @Override
    public RenderTarget getFrontBuffer() {
        return frontBuffer;
    }

    @Override
    public long getWindowHandle() {
        return window;
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
    public long waitForGpuSync(long gpuCommandSync) {
        long start = System.nanoTime();
        while(true) {
            if (isSignaled(gpuCommandSync)) break;
        }
        return System.nanoTime() - start;
    }

    @Override
    public boolean isSignaled(long gpuCommandSync) {
        return calculate(() -> {
            if(gpuCommandSync > 0) {
                int signaled = glClientWaitSync(gpuCommandSync, GL_SYNC_FLUSH_COMMANDS_BIT, 0);
                if(signaled == GL_ALREADY_SIGNALED || signaled == GL_CONDITION_SATISFIED ) {
                    return true;
                }
                return false;
            }
            return true;
        });
    }

    @Override
    public void createNewGPUFenceForReadState(RenderState currentReadState) {
        long readStateSync = currentReadState.getGpuCommandSync();
        if(readStateSync > 0) {
            glDeleteSync(readStateSync);
        }
        currentReadState.setGpuCommandSync(glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0));
    }

    @Override
    public void registerPerFrameCommand(PerFrameCommandProvider perFrameCommandProvider) {
        this.perFrameCommandProviders.add(perFrameCommandProvider);
    }

    @Override
    public boolean isError() {
        return calculate(() -> GL11.glGetError() != GL11.GL_NO_ERROR);
    }

    private final void privateInit() throws LWJGLException {
        glfwSetErrorCallback(errorCallback = GLFWErrorCallback.createPrint(System.err));
        glfwInit();
        glfwWindowHint(GLFW_RESIZABLE, GL_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(Config.getInstance().getWidth(), Config.getInstance().getHeight(), "HPEngine", 0, 0);
        if(window == 0) {
            throw new RuntimeException("Failed to create window");
        }

        glfwMakeContextCurrent(window);
        framebufferSizeCallback = new GLFWFramebufferSizeCallback() {
            @Override
            public void invoke(long window, int width, int height) {
                try {
                    OpenGLContext.this.setCanvasWidth(width);
                    OpenGLContext.this.setCanvasHeight(height);
                } catch (Exception e) {
                }
            }
        };
        glfwSetFramebufferSizeCallback(window, framebufferSizeCallback);
        glfwSetWindowCloseCallback(window, closeCallback);

        glfwSetInputMode(window, GLFW_STICKY_KEYS, 1);
        glfwSwapInterval(1);
        GL.createCapabilities();
        glfwShowWindow(window);

        this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
//        GL43.glDebugMessageCallback(new KHRDebugCallback(handler));

        enable(GlCap.DEPTH_TEST);
        enable(GlCap.CULL_FACE);

        // Map the internal OpenGL coordinate system to the entire screen
        viewPort(0, 0, Config.getInstance().getWidth(), Config.getInstance().getHeight());
        maxTextureUnits = (GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS));

        frontBuffer = createFrontBuffer();

        initialized = true;
        LOGGER.info("OpenGLContext initialized");
    }

    @Override
    public void update(float seconds) {
        try {
            executePerFrameCommands();
            commandQueue.executeCommands();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executePerFrameCommands() {
        for(int i = 0; i < perFrameCommandProviders.size(); i++) {
            PerFrameCommandProvider provider = perFrameCommandProviders.get(i);
            executePerFrameCommand(provider);
        }
    }

    private void executePerFrameCommand(PerFrameCommandProvider perFrameCommandProvider) {
        if(perFrameCommandProvider.isReadyForExecution()) {
            perFrameCommandProvider.getDrawCommand().run();
            perFrameCommandProvider.postRun();
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
        execute(() -> {
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
        execute(() -> {
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
        execute(() -> {
            GL44.glBindTextures(0, textureIds);
        });
    }
    @Override
    public void bindTextures(int count, IntBuffer textureIds) {
        execute(() -> {
            GL44.glBindTextures(0, textureIds);
        });
    }
    @Override
    public void bindTextures(int firstUnit, int count, IntBuffer textureIds) {
        execute(() -> {
            GL44.glBindTextures(firstUnit, textureIds);
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
        return calculate(() -> GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX));
    }
    @Override
    public int getAvailableTotalVRAM() {
        return calculate(() -> GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX));
    }
    @Override
    public int getDedicatedVRAM() {
        return calculate(() -> GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX));
    }
    @Override
    public int getEvictedVRAM() {
        return calculate(() -> GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_EVICTED_MEMORY_NVX));
    }
    @Override
    public int getEvictionCount() {
        return calculate(() -> GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX));
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
            return (RETURN_TYPE) execute(new FutureCallable() {
                @Override
                public RETURN_TYPE execute() throws Exception {
                    return callable.call();
                }
            }).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public <RETURN_TYPE> CompletableFuture<RETURN_TYPE> execute(FutureCallable<RETURN_TYPE> command) {
        if(isOpenGLThread()) {
            try {
                command.getFuture().complete(command.execute());
                return command.getFuture();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return commandQueue.addCommand(command);
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
    public TimeStepThread getGpuThread() {
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
//        GLTimerQuery.getInstance().begin();
//        runnable.run();
//        GLTimerQuery.getInstance().end();
//        LOGGER.info(GLTimerQuery.getInstance().getResult().toString());
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


    private void init() {
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

    static void waitForInitialization(GpuContext context) {
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


    private final QuadVertexBuffer fullscreenBuffer;
    private final QuadVertexBuffer debugBuffer;
    @Override
    public VertexBuffer getFullscreenBuffer() {
        return fullscreenBuffer;
    }
    @Override
    public VertexBuffer getDebugBuffer() {
        return debugBuffer;
    }
}
