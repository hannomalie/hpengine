package test;

import java.io.File;

import main.World;
import main.model.Entity;
import main.model.IEntity;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class EntityTest extends TestWithRenderer {
	
	@Test
	public void writeAndRead() throws Exception {
		Entity entity = (Entity) renderer.getEntityFactory().getEntity(renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sphere.obj")).get(0));
		
		String filename = "default.hpentity";

		Assert.assertTrue(Entity.write(entity, filename));
	      
		IEntity loadedEntity = renderer.getEntityFactory().read(filename);
	    
	    Assert.assertTrue(entity.equals(loadedEntity));
	    
	}
}
