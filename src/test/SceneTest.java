package test;

import main.World;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.scene.Scene;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class SceneTest extends TestWithRenderer {
	private static final String SCENENAME = "__testscene";

	@Test
	public void writeAndRead() throws Exception {
		Scene scene = new Scene(renderer);
		scene.init(renderer);
		Assert.assertTrue(scene.write(SCENENAME));
		
		Scene loadedScene = Scene.read(renderer, SCENENAME);
	      
	    Assert.assertNotNull(scene.equals(loadedScene));
	    
	}
}
