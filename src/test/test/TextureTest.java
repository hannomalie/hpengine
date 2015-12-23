package test;

import ddsutil.ImageRescaler;
import jogl.DDSImage;
import model.DDSFile;
import net.nikr.dds.DDSUtil;
import org.junit.Assert;
import org.junit.Test;
import texture.CubeMap;
import texture.Texture;
import texture.TextureFactory;
import util.Util;
import util.stopwatch.StopWatch;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class TextureTest extends TestWithAppContext {

	@Test
	public void writeAndRead() throws IOException, ClassNotFoundException {
        Texture texture = TextureFactory.getInstance().getTexture("hp/assets/textures/wood_diffuse.png");
		
		byte[] data = texture.getData();
		
		String filename = "wood_diffuse.hptexture";

		assertTrue(Texture.write(texture, filename));

        assertTrue(TextureFactory.getInstance().removeTexture("hp/assets/textures/wood_diffuse.png"));

        texture = TextureFactory.getInstance().getTexture("hp/assets/textures/wood_diffuse.png");
	    
	    assertArrayEquals(data, texture.getData());
	    
	}

    @Test
    public void testDDSLoad() throws IOException {
        String pathToSourceTexture = "hp/assets/textures/stone_reflection.png";
        BufferedImage readTexture = TextureFactory.getInstance().loadImage(pathToSourceTexture);
        File targetFile = new File("hp/assets/textures/test_output_texture.dds");

        readTexture = Texture.rescaleToNextPowerOfTwo(readTexture);

        if(targetFile.exists()) { assertTrue(targetFile.delete()); }
        writeFile(targetFile, readTexture, DDSImage.D3DFMT_DXT5, true);
    }

    private void writeFile(File file, BufferedImage bi,
                           int pixelformat, Boolean generateMipMaps) throws IOException {
        ddsutil.DDSUtil.write(file, bi, pixelformat, generateMipMaps);
        assertEquals("file was created", true, file.exists());

        long start = System.currentTimeMillis();
        DDSImage newddsimage = DDSImage.read(file);
        System.out.println("DDS read took " + (System.currentTimeMillis()-start) + "ms");

        assertEquals("height",bi.getHeight(), newddsimage.getHeight());
        assertEquals("width", bi.getWidth(), newddsimage.getWidth());
        assertEquals("pixelformat", pixelformat, newddsimage.getPixelFormat());
        if(generateMipMaps)
            assertEquals("number of MipMaps", Util.calculateMipMapCount(Math.max(bi.getWidth(), bi.getHeight())), newddsimage.getNumMipMaps()-1);
    }

	@Test
	public void loadsTexture() throws IOException {
        Texture texture = TextureFactory.getInstance().getTexture("hp/assets/textures/test_test_test.png");
	}
	
	@Test
	public void loadsCubeMap() throws IOException {
        CubeMap cubeMap = TextureFactory.getInstance().getCubeMap("hp/assets/textures/wood_diffuse.png");
	}
}
