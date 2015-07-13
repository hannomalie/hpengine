package test;

import engine.World;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import renderer.Renderer;

public class TestWithWorld {

	public static World world;
	public static Renderer renderer;

	public TestWithWorld() {
		super();
	}
	
	@BeforeClass
	public static void init() {
		world = new World(true);
		renderer = world.getRenderer();
	}
	
	@AfterClass
	public static void kill() {
		world.destroy();
	}
}