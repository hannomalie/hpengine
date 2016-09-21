package test;

import engine.AppContext;
import engine.model.Entity;
import engine.model.EntityFactory;
import engine.model.Model;
import engine.model.OBJLoader;
import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;
import scene.Scene;

import java.io.File;
import java.util.List;

public class SceneTest extends TestWithAppContext {
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
        appContext.setScene(scene);
        Entity entity = EntityFactory.getInstance().getEntity();
        scene.add(entity);
        Assert.assertEquals(1, appContext.getScene().getEntities().size());
        Assert.assertTrue(scene.write(SCENENAME));

        Scene loadedScene = Scene.read(SCENENAME);
        loadedScene.init();
        appContext.setScene(loadedScene);

        Assert.assertEquals(1, appContext.getScene().getEntities().size());
    }

}
