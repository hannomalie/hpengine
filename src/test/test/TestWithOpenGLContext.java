package test;

import org.junit.BeforeClass;
import org.lwjgl.LWJGLException;
import renderer.OpenGLContext;

public class TestWithOpenGLContext {

    @BeforeClass
    public static void init() throws LWJGLException {
        OpenGLContext.getInstance().init();
    }
}
