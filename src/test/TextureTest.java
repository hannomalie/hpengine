package test;

import java.io.IOException;

import main.World;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.texture.CubeMap;
import main.texture.Texture;
import main.util.stopwatch.StopWatch;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TextureTest extends TestWithRenderer {

	@Test
	public void writeAndRead() throws IOException, ClassNotFoundException {
		Texture texture = renderer.getTextureFactory().getTexture("assets/textures/wood_diffuse.png");
		
		byte[] data = texture.getData();
		
		String filename = "wood_diffuse.hptexture";

		Assert.assertTrue(Texture.write(texture, filename));
	      
        texture = renderer.getTextureFactory().getTexture(filename);
	    
		StopWatch.ACTIVE = false;
	    Assert.assertArrayEquals(data, texture.getData());
	    
	}
	
	@Test
	public void loadsCubeMap() throws IOException {
		CubeMap cubeMap = renderer.getTextureFactory().getCubeMap("assets/textures/wood_diffuse.png");
	}
}
