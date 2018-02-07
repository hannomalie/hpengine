package de.hanno.hpengine;

import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.util.stopwatch.StopWatch;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class MaterialTest extends TestWithEngine {

	@Test
	public void writeAndRead() throws IOException, ClassNotFoundException {
        Material material = engine.getMaterialFactory().getDefaultMaterial();
		
		String filename = "default.hpmaterial";

		Assert.assertTrue(Material.write(material, filename));

        Material loadedMaterial = engine.getMaterialFactory().read(filename);
	    
		StopWatch.ACTIVE = false;
	    Assert.assertTrue(material.equals(loadedMaterial));
	    
	}
}
