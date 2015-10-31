package renderer;

import config.Config;
import engine.AppContext;
import engine.TimeStepThread;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;
import org.lwjgl.opengl.DisplayMode;
import renderer.command.Command;
import renderer.command.Result;
import renderer.constants.*;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static renderer.constants.GlCap.CULL_FACE;
import static renderer.constants.GlCap.DEPTH_TEST;

public class OpenGLContext {

    public static String OPENGL_THREAD_NAME;
    private final TimeStepThread openGLThread;
    private Renderer renderer = null;
    private Canvas canvas;
    private boolean attached;

    private static OpenGLContext instance;

    private BlockingQueue<Command> workQueue = new LinkedBlockingQueue<Command>();
    private Map<Command<? extends Result<?>>, SynchronousQueue<Result<? extends Object>>> commandQueueMap = new ConcurrentHashMap<>();


    public static OpenGLContext getInstance() {
        if(instance == null) {
            throw new IllegalStateException("OpenGL context not initialized. Init a renderer first.");
        }
        return instance;
    }

    OpenGLContext(Renderer renderer) throws LWJGLException {
        this(renderer, false);
    }

    OpenGLContext(Renderer renderer, boolean headless) throws LWJGLException {
        this(renderer, null, headless);
    }
    OpenGLContext(Renderer renderer, Canvas canvas, boolean headless) throws LWJGLException {

        openGLThread = new TimeStepThread("OpenGLContext", 0.0f) {

            @Override
            public void update(float seconds) {
                if (!isInitialized()) {
                    try {
                        init(renderer, canvas);
                    } catch (LWJGLException e) {
                        e.printStackTrace();
                        System.exit(-1);
                    }
                } else {
                    instance.update(seconds);
                }
            }
        };
        openGLThread.start();

        while(!isInitialized()) {

        }
    }

    private void init(Renderer renderer, Canvas canvas) throws LWJGLException {
        this.canvas = canvas;
        this.renderer = renderer;

        PixelFormat pixelFormat = new PixelFormat();
        ContextAttribs contextAttributes = new ContextAttribs(4, 3)
//				.withProfileCompatibility(true)
//				.withForwardCompatible(true)
                .withProfileCore(true)
                .withDebug(true)
                ;

        Display.setDisplayMode(new DisplayMode(Config.WIDTH, Config.HEIGHT));
        Display.setTitle("DeferredRenderer");
        Display.create(pixelFormat, contextAttributes);
        this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        Display.setResizable(false);
        KHRDebugCallback.Handler handler = new KHRDebugCallback.Handler() {
            @Override
            public void handleMessage(int source, int type, int id, int severity, String message) {
                if(severity == KHRDebug.GL_DEBUG_SEVERITY_HIGH) {
                    Logger.getGlobal().severe(message);
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

        instance = this;
    }

    public void update(float seconds) {
        try {
            executeCommands();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeCommands() throws Exception {
        Command command = workQueue.poll();
        while(command != null) {
            Result result = command.execute(AppContext.getInstance());
            SynchronousQueue<Result<?>> queue = commandQueueMap.get(command);
            try {
                queue.offer(result);

            } catch (NullPointerException e) {
                Logger.getGlobal().info("Got null for command " + command.toString());
            }
            command = workQueue.poll();
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
        if(activeTexture != textureIndexGLInt) {
            GL13.glActiveTexture(textureIndexGLInt);
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
        AppContext.getInstance().getRenderer().getOpenGLContext().doWithOpenGLContext(() -> {
            GL11.glBindTexture(target.glTarget, textureId);
            textureBindings.put(getCleanedTextureUnitValue(activeTexture), textureId);
        });
    }
    public void bindTexture(int textureUnitIndex, GlTextureTarget target, int textureId) {
        AppContext.getInstance().getRenderer().getOpenGLContext().doWithOpenGLContext(() -> {
        // TODO: Use when no bypassing calls to bindtexture any more
//        if(!textureBindings.containsKey(textureUnitIndex) ||
//           (textureBindings.containsKey(textureUnitIndex) && textureId != textureBindings.get(textureUnitIndex))) {
            activeTexture(textureUnitIndex);
            GL11.glBindTexture(target.glTarget, textureId);
            textureBindings.put(textureUnitIndex, textureId);
//        }
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
        GL11.glClearColor(r, g, b, a);
    }

    public void bindImageTexture(int unit, int textureId, int level, boolean layered, int layer, int access, int internalFormat) {
        // TODO: create access enum
        GL42.glBindImageTexture(unit, textureId, level, layered, layer, access, internalFormat);
    }

    public int genTextures() {
        return GL11.glGenTextures();
    }

    public boolean isInitialized() {
        return instance != null;
    }

    private void executeCommands(AppContext appContext) throws Exception {
        Command command = workQueue.poll();
        while(command != null) {
            Result result = command.execute(appContext);
            SynchronousQueue<Result<?>> queue = commandQueueMap.get(command);
            try {
                queue.offer(result);

            } catch (NullPointerException e) {
                Logger.getGlobal().info("Got null for command " + command.toString());
            }
            command = workQueue.poll();
        }
    }

    public <OBJECT_TYPE, RESULT_TYPE extends Result<OBJECT_TYPE>> SynchronousQueue<RESULT_TYPE> addCommand(Command<RESULT_TYPE> command) {
        SynchronousQueue<RESULT_TYPE> queue = new SynchronousQueue<>();
        commandQueueMap.put(command, (SynchronousQueue<Result<? extends Object>>) queue);
        workQueue.offer(command);
        return queue;
    }

    public void doWithOpenGLContext(Runnable runnable) {
        doWithOpenGLContext(runnable, true);
    }
    public void doWithOpenGLContext(Runnable runnable, boolean andBlock) {

        if(util.Util.isOpenGLThread()) {
            runnable.run();
        } else {
            SynchronousQueue<Result<Object>> queue = addCommand(new Command<Result<Object>>() {
                @Override
                public Result<Object> execute(AppContext appContext) {
                    runnable.run();
                    return new Result<Object>(true);
                }
            }
            );
            if(andBlock) {
                try {
                    queue.poll(5, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public <TYPE> TYPE calculateWithOpenGLContext(Callable<TYPE> callable) {
        if(util.Util.isOpenGLThread()) {
            try {
                return callable.call();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            final TYPE[] temp = (TYPE[])new Object[1];
            SynchronousQueue<Result<Object>> queue = addCommand(new Command<Result<Object>>() {
                     @Override
                     public Result<Object> execute(AppContext appContext) {
                         try {
                             temp[0] = callable.call();
                         } catch (Exception e) {
                             e.printStackTrace();
                         }
                         return new Result<Object>(true);
                     }
                 }
            );
            try {
                queue.poll(5, TimeUnit.MINUTES);
                return temp[0];
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public TimeStepThread getDrawThread() {
        return openGLThread;
    }
}
