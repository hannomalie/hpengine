package test;

import engine.Engine;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import renderer.Renderer;

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
