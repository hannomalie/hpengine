package renderer;

import config.Config;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;
import org.lwjgl.opengl.DisplayMode;
import renderer.constants.BlendMode;
import renderer.constants.GlCap;
import renderer.constants.GlDepthFunc;
import renderer.constants.GlTextureTarget;

import java.awt.*;
import java.util.HashMap;
import java.util.logging.Logger;

public class OpenGLContext {

    private Renderer renderer = null;
    private Canvas canvas;
    private boolean attached;

    private static OpenGLContext instance;
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

        this.canvas = canvas;
        this.renderer = renderer;

        PixelFormat pixelFormat = new PixelFormat();
        ContextAttribs contextAttributes = new ContextAttribs(4, 3)
				.withProfileCompatibility(true)
				.withForwardCompatible(true)
                .withProfileCore(true)
                .withDebug(true)
                ;

        Display.setDisplayMode(new DisplayMode(Config.WIDTH, Config.HEIGHT));
        Display.setVSyncEnabled(false);
        Display.setTitle("DeferredRenderer");
        Display.create(pixelFormat, contextAttributes);
        this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        Display.setResizable(false);
        Display.setVSyncEnabled(Config.VSYNC_ENABLED);
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

//		GL11.glClearColor(0.4f, 0.6f, 0.9f, 0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
//		GL11.glDisable(GL11.GL_CULL_FACE);

        // Map the internal OpenGL coordinate system to the entire screen
        GL11.glViewport(0, 0, Config.WIDTH, Config.HEIGHT);

        instance = this;
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
        int textureIndexGLInt = GL13.GL_TEXTURE0 + textureUnitIndex;
        if(activeTexture != textureIndexGLInt) {
            GL13.glActiveTexture(textureIndexGLInt);
        }
    }

    private HashMap<Integer, Integer> textureBindings = new HashMap<>();
    public void bindTexture(int textureUnitIndex, GlTextureTarget target, int textureId) {
        // TODO: Use when no bypassing calls to bindtexture any more
//        if(!textureBindings.containsKey(textureUnitIndex) ||
//           (textureBindings.containsKey(textureUnitIndex) && textureId != textureBindings.get(textureUnitIndex))) {
            activeTexture(textureUnitIndex);
            GL11.glBindTexture(target.glTarget, textureId);
            textureBindings.put(textureUnitIndex, textureId);
//        }
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
}
