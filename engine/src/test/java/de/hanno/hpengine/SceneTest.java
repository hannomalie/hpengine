package de.hanno.hpengine;

import de.hanno.hpengine.engine.entity.Entity;
import org.junit.Assert;
import org.junit.Test;
import de.hanno.hpengine.engine.scene.Scene;

public class SceneTest extends TestWithEngine {
	private static final String SCENENAME = "__testscene";

	@Test
	public void writeAndRead() throws Exception {
		Scene scene = new Scene(engine);
		scene.init(engine);
		Assert.assertTrue(scene.write(SCENENAME));

		Scene loadedScene = Scene.read(SCENENAME);

		Assert.assertNotNull(scene.equals(loadedScene));

	}

    @Test
    public void loadScene() throws Exception {
        Scene scene = new Scene(engine);
        scene.init(engine);
        engine.getSceneManager().setScene(scene);
        Entity entity = engine.getEntityManager().getEntity();
        scene.add(entity);
        Assert.assertEquals(1, engine.getSceneManager().getScene().getEntities().size());
        Assert.assertTrue(scene.write(SCENENAME));

        Scene loadedScene = Scene.read(SCENENAME);
        loadedScene.init(engine);
        engine.getSceneManager().setScene(loadedScene);

        Assert.assertEquals(1, engine.getSceneManager().getScene().getEntities().size());
    }

}
