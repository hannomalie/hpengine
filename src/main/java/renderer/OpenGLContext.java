package renderer;

import config.Config;
import engine.AppContext;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;
import org.lwjgl.opengl.DisplayMode;
import renderer.constants.*;

import java.awt.*;
import java.util.HashMap;
import java.util.logging.Logger;

import static renderer.constants.GlCap.CULL_FACE;
import static renderer.constants.GlCap.DEPTH_TEST;

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

        enable(DEPTH_TEST);
        enable(CULL_FACE);

        // Map the internal OpenGL coordinate system to the entire screen
        viewPort(0, 0, Config.WIDTH, Config.HEIGHT);

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
        AppContext.getInstance().getRenderer().doWithOpenGLContext(() -> {
            GL11.glBindTexture(target.glTarget, textureId);
            textureBindings.put(getCleanedTextureUnitValue(activeTexture), textureId);
        });
    }
    public void bindTexture(int textureUnitIndex, GlTextureTarget target, int textureId) {
        AppContext.getInstance().getRenderer().doWithOpenGLContext(() -> {
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
}