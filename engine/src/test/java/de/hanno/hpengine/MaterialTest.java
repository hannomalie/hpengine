package de.hanno.hpengine;

import de.hanno.hpengine.engine.model.material.SimpleMaterial;
import de.hanno.hpengine.util.stopwatch.StopWatch;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class MaterialTest extends TestWithEngine {

	@Test
	public void writeAndRead() throws IOException, ClassNotFoundException {
        SimpleMaterial material = engine.getScene().getMaterialManager().getDefaultMaterial();
		
		String filename = "default.hpmaterial";

		Assert.assertTrue(SimpleMaterial.Companion.write(material, filename));

        SimpleMaterial loadedMaterial = engine.getScene().getMaterialManager().read(filename);
	    
		StopWatch.ACTIVE = false;
	    Assert.assertTrue(material.equals(loadedMaterial));
	    
	}
}
