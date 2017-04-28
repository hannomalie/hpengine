package de.hanno.hpengine.test;

import de.hanno.hpengine.engine.CanvasWrapper;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.renderer.Renderer;
import org.junit.BeforeClass;

import java.awt.*;

public class TestWithRenderer {

	public static Renderer renderer;
	private static Engine engine;

	public TestWithRenderer() {
		super();
	}
	
	@BeforeClass
	public static void init() {
		Engine.init(new CanvasWrapper(new Canvas(), () -> {}));
		engine = Engine.getInstance();
        renderer = Renderer.getInstance();
	}
}
