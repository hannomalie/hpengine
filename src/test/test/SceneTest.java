package test;

import org.junit.Assert;
import org.junit.Test;
import scene.Scene;

public class SceneTest extends TestWithWorld {
	private static final String SCENENAME = "__testscene";

	@Test
	public void writeAndRead() throws Exception {
		Scene scene = new Scene();
		scene.init(world);
		Assert.assertTrue(scene.write(SCENENAME));
		
		Scene loadedScene = Scene.read(renderer, SCENENAME);
	      
	    Assert.assertNotNull(scene.equals(loadedScene));
	    
	}
}
