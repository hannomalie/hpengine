package test;

import java.io.File;
import java.io.IOException;

import main.model.Entity;
import main.model.IEntity;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.Spotlight;
import main.util.stopwatch.StopWatch;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class EntityTest {
	
	private static Renderer renderer;

	@BeforeClass
	public static void init() {
		renderer = new DeferredRenderer(new Spotlight(true));
	}

	@Test
	public void writeAndRead() throws IOException, ClassNotFoundException {
		Entity entity = (Entity) renderer.getEntityFactory().getEntity(renderer.getOBJLoader().loadTexturedModel(new File("C:\\sphere.obj")).get(0));
		
		String filename = "default.hpentity";

		Assert.assertTrue(Entity.write(entity, filename));
	      
		IEntity loadedEntity = renderer.getEntityFactory().read(filename);
	    
	    Assert.assertTrue(entity.equals(loadedEntity));
	    
	}
}
