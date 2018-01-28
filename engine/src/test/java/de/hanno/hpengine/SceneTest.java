package de.hanno.hpengine;

import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.EntityFactory;
import org.junit.Assert;
import org.junit.Test;
import de.hanno.hpengine.engine.scene.Scene;

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
        engine.getSceneManager().setScene(scene);
        Entity entity = EntityFactory.getInstance().getEntity();
        scene.add(entity);
        Assert.assertEquals(1, engine.getSceneManager().getScene().getEntities().size());
        Assert.assertTrue(scene.write(SCENENAME));

        Scene loadedScene = Scene.read(SCENENAME);
        loadedScene.init();
        engine.getSceneManager().setScene(loadedScene);

        Assert.assertEquals(1, engine.getSceneManager().getScene().getEntities().size());
    }

}
