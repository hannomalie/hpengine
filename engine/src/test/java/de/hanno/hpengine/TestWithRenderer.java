package de.hanno.hpengine;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import org.junit.BeforeClass;

public class TestWithRenderer {

	public static Renderer renderer;
	private static Engine engine;

	public TestWithRenderer() {
		super();
	}
	
	@BeforeClass
	public static void init() {
		Engine.init();
		engine = Engine.getInstance();
        renderer = Renderer.getInstance();
	}
}
