package test;

import engine.World;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import renderer.Renderer;

public class TestWithRenderer {

	public static Renderer renderer;
	
	public TestWithRenderer() {
		super();
	}
	
	@BeforeClass
	public static void init() {
		World world = new World(true);
		renderer = world.getRenderer();
	}
	
	@AfterClass
	public static void kill() {
		renderer.destroy();
	}
}