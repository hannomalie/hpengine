package test;

import java.io.File;
import java.io.IOException;

import main.World;
import main.model.Entity;
import main.model.IEntity;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.DirectionalLight;
import main.util.stopwatch.StopWatch;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class EntityTest {
	
	private static Renderer renderer;

	@BeforeClass
	public static void init() {
		renderer = new DeferredRenderer(new DirectionalLight(true));
	}

	@Test
	public void writeAndRead() throws Exception {
		Entity entity = (Entity) renderer.getEntityFactory().getEntity(renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sphere.obj")).get(0));
		
		String filename = "default.hpentity";

		Assert.assertTrue(Entity.write(entity, filename));
	      
		IEntity loadedEntity = renderer.getEntityFactory().read(filename);
	    
	    Assert.assertTrue(entity.equals(loadedEntity));
	    
	}
}
