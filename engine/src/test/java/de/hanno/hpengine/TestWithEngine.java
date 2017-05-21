package de.hanno.hpengine;

import de.hanno.hpengine.engine.ApplicationFrame;
import de.hanno.hpengine.engine.CanvasWrapper;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.renderer.Renderer;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.awt.*;

public class TestWithEngine {

	public static Renderer renderer;
    public static Engine engine;

    public TestWithEngine() {
		super();
	}
	
	@BeforeClass
	public static void init() {
        // TODO: Make this work headless
		ApplicationFrame frame = new ApplicationFrame();
		Engine.init(frame.getRenderCanvas());
        engine = Engine.getInstance();
        renderer = Renderer.getInstance();
	}
	
	@AfterClass
	public static void kill() {
//        Engine.getInstance().destroy();
	}
}
