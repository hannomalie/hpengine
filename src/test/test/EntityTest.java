package test;

import engine.AppContext;
import engine.model.Entity;
import engine.model.EntityFactory;
import engine.model.Model;
import engine.model.OBJLoader;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.List;

public class EntityTest extends TestWithAppContext {

	@Test
	public void writeAndRead() throws Exception {
        Entity entity = EntityFactory.getInstance().getEntity(new OBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/sphere.obj")).get(0));
		entity.setName("default");

		String filename = "default.hpentity";

		Assert.assertTrue(Entity.write(entity, filename));

        Entity loadedEntity = EntityFactory.getInstance().read(filename);
		Assert.assertTrue(entity.equals(loadedEntity));

	}
	@Test
	public void loadParented() throws Exception {
        List<Model> models = new OBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/cornellbox.obj"));
        Entity entity = EntityFactory.getInstance().getEntity("xxx", models);
		appContext.getScene().add(entity);

		Assert.assertTrue(appContext.getScene().getEntities().contains(entity));
		Assert.assertTrue(entity.hasChildren());
		Assert.assertTrue(entity.getName().equals("xxx"));
		for(Model model : models) {
			boolean containsEntity = false;
			for (Entity child : entity.getChildren()) {
				if(child.getName().equals(model.getName())) { containsEntity = true; }
				Assert.assertTrue(appContext.getScene().getEntities().contains(child));
			}
			Assert.assertTrue(containsEntity);
		}


	}
}
