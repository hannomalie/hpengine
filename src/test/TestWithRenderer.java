package test;

import main.World;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;

import org.junit.Before;
import org.junit.BeforeClass;

public class TestWithRenderer {

	public static Renderer renderer;
	
	public TestWithRenderer() {
		super();
	}
	
	@BeforeClass
	public static void init() {
		World world = new World();
		renderer = world.getRenderer();
	}

}