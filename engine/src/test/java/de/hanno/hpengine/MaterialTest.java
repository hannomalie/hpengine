package de.hanno.hpengine;

import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import org.junit.Assert;
import org.junit.Test;
import de.hanno.hpengine.util.stopwatch.StopWatch;

import java.io.IOException;

public class MaterialTest extends TestWithRenderer {

	@Test
	public void writeAndRead() throws IOException, ClassNotFoundException {
        Material material = MaterialFactory.getInstance().getDefaultMaterial();
		
		String filename = "default.hpmaterial";

		Assert.assertTrue(Material.write(material, filename));

        Material loadedMaterial = MaterialFactory.getInstance().read(filename);
	    
		StopWatch.ACTIVE = false;
	    Assert.assertTrue(material.equals(loadedMaterial));
	    
	}
}
