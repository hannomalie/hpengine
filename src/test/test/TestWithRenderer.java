package test;

import engine.AppContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import renderer.Renderer;

public class TestWithRenderer {

	public static Renderer renderer;
	private static AppContext appContext;

	public TestWithRenderer() {
		super();
	}
	
	@BeforeClass
	public static void init() {
		AppContext.init();
		appContext = AppContext.getInstance();
		renderer = appContext.getRenderer();
	}
	
	@AfterClass
	public static void kill() {
		renderer.destroy();
	}
}