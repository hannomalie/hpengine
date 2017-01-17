package de.hanno.hpengine.test;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.renderer.Renderer;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class TestWithEngine {

	public static Renderer renderer;
    public static Engine engine;

    public TestWithEngine() {
		super();
	}
	
	@BeforeClass
	public static void init() {
        // TODO: Make this work headless
        Engine.init(false);
        engine = Engine.getInstance();
        renderer = Renderer.getInstance();
	}
	
	@AfterClass
	public static void kill() {
//        Engine.getInstance().destroy();
	}
}
