package test;

import org.junit.Assert;
import org.junit.Test;
import texture.CubeMap;
import texture.Texture;
import util.stopwatch.StopWatch;

import java.io.IOException;

public class TextureTest extends TestWithAppContext {

	@Test
	public void writeAndRead() throws IOException, ClassNotFoundException {
		Texture texture = renderer.getTextureFactory().getTexture("hp/assets/textures/wood_diffuse.png");
		
		byte[] data = texture.getData();
		
		String filename = "wood_diffuse.hptexture";

		Assert.assertTrue(Texture.write(texture, filename));

        Assert.assertTrue(appContext.getRenderer().getTextureFactory().removeTexture("hp/assets/textures/wood_diffuse.png"));
	      
        texture = renderer.getTextureFactory().getTexture("hp/assets/textures/wood_diffuse.png");
	    
	    Assert.assertArrayEquals(data, texture.getData());
	    
	}

	@Test
	public void loadsTexture() throws IOException {
		Texture texture = renderer.getTextureFactory().getTexture("hp/assets/textures/test_test_test.png");
	}
	
	@Test
	public void loadsCubeMap() throws IOException {
		CubeMap cubeMap = renderer.getTextureFactory().getCubeMap("hp/assets/textures/wood_diffuse.png");
	}
}
