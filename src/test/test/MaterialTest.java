package test;

import org.junit.Assert;
import org.junit.Test;
import renderer.material.Material;
import util.stopwatch.StopWatch;

import java.io.IOException;

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
