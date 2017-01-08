package test;

import engine.model.Entity;
import engine.model.EntityFactory;
import org.junit.Assert;
import org.junit.Test;
import scene.Scene;

public class SceneTest extends TestWithEngine {
	private static final String SCENENAME = "__testscene";

	@Test
	public void writeAndRead() throws Exception {
		Scene scene = new Scene();
		scene.init();
		Assert.assertTrue(scene.write(SCENENAME));

		Scene loadedScene = Scene.read(SCENENAME);

		Assert.assertNotNull(scene.equals(loadedScene));

	}

    @Test
    public void loadScene() throws Exception {
        Scene scene = new Scene();
        scene.init();
        engine.setScene(scene);
        Entity entity = EntityFactory.getInstance().getEntity();
        scene.add(entity);
        Assert.assertEquals(1, engine.getScene().getEntities().size());
        Assert.assertTrue(scene.write(SCENENAME));

        Scene loadedScene = Scene.read(SCENENAME);
        loadedScene.init();
        engine.setScene(loadedScene);

        Assert.assertEquals(1, engine.getScene().getEntities().size());
    }

}
