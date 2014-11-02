package test;

import java.io.File;

import main.World;
import main.model.Entity;
import main.model.IEntity;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.Spotlight;
import main.scene.Scene;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class SceneTest {
	private static Renderer renderer;
	private static final String SCENENAME = "__testscene";
	
	@BeforeClass
	public static void init() {
		renderer = new DeferredRenderer(new Spotlight(true));
	}

	@Test
	public void writeAndRead() throws Exception {
		Scene scene = new Scene(renderer);
		scene.init(renderer);
		Assert.assertTrue(scene.write(SCENENAME));
		
		Scene loadedScene = Scene.read(renderer, SCENENAME);
	      
	    Assert.assertNotNull(scene.equals(loadedScene));
	    
	}
}
