package test;

import engine.AppContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import renderer.Renderer;

public class TestWithWorld {

	public static AppContext appContext;
	public static Renderer renderer;

	public TestWithWorld() {
		super();
	}
	
	@BeforeClass
	public static void init() {
        AppContext.init(true);
		appContext = AppContext.getInstance();
		renderer = appContext.getRenderer();
	}
	
	@AfterClass
	public static void kill() {
		appContext.destroy();
	}
}