package test;

import engine.AppContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import renderer.Renderer;

public class TestWithAppContext {

	public static Renderer renderer;
    public static AppContext appContext;

    public TestWithAppContext() {
		super();
	}
	
	@BeforeClass
	public static void init() {
        AppContext.init(true);
        appContext = AppContext.getInstance();
        renderer = Renderer.getInstance();
	}
	
	@AfterClass
	public static void kill() {
        AppContext.getInstance().destroy();
	}
}