package test;

import engine.Engine;
import org.junit.BeforeClass;
import renderer.Renderer;

public class TestWithRenderer {

	public static Renderer renderer;
	private static Engine engine;

	public TestWithRenderer() {
		super();
	}
	
	@BeforeClass
	public static void init() {
		Engine.init(true);
		engine = Engine.getInstance();
        renderer = Renderer.getInstance();
	}
}
