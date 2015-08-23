package renderer;

import config.Config;
import engine.World;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.*;

import java.awt.*;
import java.util.logging.Logger;

public class OpenGLContext {

    private Canvas canvas;
    private boolean attached;

    OpenGLContext() throws LWJGLException {
        this(false);
    }

    OpenGLContext(boolean headless) throws LWJGLException {
        this(null, headless);
    }
    OpenGLContext(Canvas canvas, boolean headless) throws LWJGLException {

        this.canvas = canvas;

        PixelFormat pixelFormat = new PixelFormat();
        ContextAttribs contextAtrributes = new ContextAttribs(4, 3)
				.withProfileCompatibility(true)
				.withForwardCompatible(true)
                .withProfileCore(true)
                .withDebug(true)
                ;

        Display.setDisplayMode(new org.lwjgl.opengl.DisplayMode(Config.WIDTH, Config.HEIGHT));
        Display.setVSyncEnabled(false);
        Display.setTitle("DeferredRenderer");
        Display.create(pixelFormat, contextAtrributes);
        Display.setResizable(false);
        Display.setVSyncEnabled(World.VSYNC_ENABLED);
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


//		GL11.glClearColor(0.4f, 0.6f, 0.9f, 0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
//		GL11.glDisable(GL11.GL_CULL_FACE);

        // Map the internal OpenGL coordinate system to the entire screen
        GL11.glViewport(0, 0, Config.WIDTH, Config.HEIGHT);
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

}
