package test;

import engine.World;
import engine.model.Entity;
import engine.model.Model;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.List;

public class EntityTest extends test.TestWithWorld {

	@Test
	public void writeAndRead() throws Exception {
		Entity entity = renderer.getEntityFactory().getEntity(renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sphere.obj")).get(0));
		entity.setName("default");

		String filename = "default.hpentity";

		Assert.assertTrue(Entity.write(entity, filename));

		Entity loadedEntity = renderer.getEntityFactory().read(filename);
		Assert.assertTrue(entity.equals(loadedEntity));

	}
	@Test
	public void loadParented() throws Exception {
		List<Model> models = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/cornellbox.obj"));
		Entity entity = renderer.getEntityFactory().getEntity("xxx", models);
		world.getScene().add(entity);

		Assert.assertTrue(world.getScene().getEntities().contains(entity));
		Assert.assertTrue(entity.hasChildren());
		Assert.assertTrue(entity.getName().equals("xxx"));
		for(Model model : models) {
			boolean containsEntity = false;
			for (Entity child : entity.getChildren()) {
				if(child.getName().equals(model.getName())) { containsEntity = true; }
				Assert.assertTrue(world.getScene().getEntities().contains(child));
			}
			Assert.assertTrue(containsEntity);
		}


	}
}
