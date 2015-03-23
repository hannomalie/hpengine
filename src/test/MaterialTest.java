package test;

import java.io.IOException;

import main.World;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.util.stopwatch.StopWatch;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MaterialTest extends TestWithRenderer {

	@Test
	public void writeAndRead() throws IOException, ClassNotFoundException {
		Material material = renderer.getMaterialFactory().getDefaultMaterial();
		
		String filename = "default.hpmaterial";

		Assert.assertTrue(Material.write(material, filename));
	      
        Material loadedMaterial = renderer.getMaterialFactory().read(filename);
	    
		StopWatch.ACTIVE = false;
	    Assert.assertTrue(material.equals(loadedMaterial));
	    
	}
}
