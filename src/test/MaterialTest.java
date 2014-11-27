package test;

import java.io.IOException;

import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.DirectionalLight;
import main.renderer.material.Material;
import main.texture.CubeMap;
import main.texture.Texture;
import main.util.Util;
import main.util.stopwatch.StopWatch;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class MaterialTest {
	
	private static Renderer renderer;

	@BeforeClass
	public static void init() {
		renderer = new DeferredRenderer(new DirectionalLight(true));
	}

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
