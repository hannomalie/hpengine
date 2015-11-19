package test;

import engine.AppContext;
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
		AppContext.init(true);
		appContext = AppContext.getInstance();
        renderer = Renderer.getInstance();
	}
}