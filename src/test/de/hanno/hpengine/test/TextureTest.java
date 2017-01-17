package de.hanno.hpengine.test;

import ddsutil.DDSUtil;
import jogl.DDSImage;
import org.junit.Assert;
import org.junit.Test;
import de.hanno.hpengine.texture.CubeMap;
import de.hanno.hpengine.texture.Texture;
import de.hanno.hpengine.texture.TextureFactory;
import de.hanno.hpengine.util.Util;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static de.hanno.hpengine.texture.Texture.UploadState.*;

public class TextureTest extends TestWithEngine {

    private static final Logger LOGGER = Logger.getLogger(TextureTest.class.getName());

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
        LOGGER.info("DDS read took " + (System.currentTimeMillis()-start) + "ms");

        assertEquals("height",bi.getHeight(), newddsimage.getHeight());
        assertEquals("width", bi.getWidth(), newddsimage.getWidth());
        assertEquals("pixelformat", pixelformat, newddsimage.getPixelFormat());
        if(generateMipMaps)
            assertEquals("number of MipMaps", Util.calculateMipMapCount(Math.max(bi.getWidth(), bi.getHeight())), newddsimage.getNumMipMaps());
    }

    @Test(timeout = 30000L)
    public void testEqualityDdsAndRegularTexture() throws IOException {
        String pathToSourceTexture = "hp/assets/textures/stone_reflection.png";
        File fileAsDds = new File(Texture.getFullPathAsDDS(pathToSourceTexture));
        Texture regularLoaded = TextureFactory.getInstance().getTexture(pathToSourceTexture);
        byte[] regularLoadedData = regularLoaded.getData();
        while(!regularLoaded.getDdsConversionState().equals(Texture.DDSConversionState.CONVERTED) || !fileAsDds.exists()) {}
        DDSImage.ImageInfo[] allMipMaps = DDSImage.read(fileAsDds).getAllMipMaps();
        DDSImage.ImageInfo highestMipMapOfDDSImage = allMipMaps[0];
        byte[] ddsDecompressedData = TextureFactory.getInstance().convertImageData(ddsutil.DDSUtil.decompressTexture(highestMipMapOfDDSImage.getData(), highestMipMapOfDDSImage.getWidth(), highestMipMapOfDDSImage.getHeight(), highestMipMapOfDDSImage.getCompressionFormat()));
        float expectedFailurePercentage = 5f;
        float actualFailurePercentage = getFailurePercentageWithTolerance(regularLoadedData, ddsDecompressedData, 2f);
        boolean qualityAchieved = actualFailurePercentage < expectedFailurePercentage;

        for(DDSImage.ImageInfo info : allMipMaps) {
            BufferedImage bufferedImage = null;
            ByteBuffer data = info.getData();
            data.rewind();

            if (info.isCompressed())
                bufferedImage = DDSUtil.decompressTexture(
                        data,
                        info.getWidth(),
                        info.getHeight(),
                        info.getCompressionFormat());
//            else
//                bufferedImage = DDSUtil.loadBufferedImageFromByteBuffer(
//                        info.getData(),
//                        info.getWidth(),
//                        info.getHeight(),
//                        image);

//            showAsTextureInFrame(info, bufferedImage, data);
        }
        Assert.assertTrue(String.format("Percentage difference not achieved (%s | %s) Failure", actualFailurePercentage, expectedFailurePercentage),qualityAchieved);
    }

    private static void showAsTextureInFrame(DDSImage.ImageInfo info, BufferedImage bufferedImage, ByteBuffer data) {
        WritableRaster wr = bufferedImage.getRaster();
        byte[] byteArray = new byte[data.capacity()];
        data.get(byteArray);
        wr.setDataElements(0,0, info.getWidth(), info.getHeight(), byteArray);

        JFrame frame = new JFrame("WINDOW");
        frame.setVisible(true);
        frame.add(new JLabel(new ImageIcon(bufferedImage)));
        frame.pack();
    }

    @Test(timeout = 30000L)
    public void testEqualityDifferentTextures() throws IOException {
        String pathToFirstSourceTexture = "hp/assets/textures/stone_reflection.png";
        Texture regularLoadedFirst = TextureFactory.getInstance().getTexture(pathToFirstSourceTexture);
        String pathToSecondSourceTexture = "hp/assets/textures/wood_diffuse.png";
        Texture regularLoadedSecond = TextureFactory.getInstance().getTexture(pathToSecondSourceTexture);
        byte[] regularLoadedFirstData = regularLoadedFirst.getData();
        byte[] regularLoadedSecondData = regularLoadedSecond.getData();

        float expectedFailurePercentage = 70f;
        float actualFailurePercentage = getFailurePercentageWithTolerance(regularLoadedFirstData, regularLoadedSecondData, 2f);
        boolean qualityAchieved = actualFailurePercentage > expectedFailurePercentage;
        Assert.assertTrue(String.format("Percentage difference not achieved (%s | min %s) Failure", actualFailurePercentage, expectedFailurePercentage),qualityAchieved);
    }

	@Test
	public void loadsTexture() throws IOException {
        Texture texture = TextureFactory.getInstance().getTexture("hp/assets/textures/test_test_test.png");
	}

    @Test
    public void loadsTextureFromDDS() throws IOException {
        Texture xxx = TextureFactory.getInstance().getTexture("hp/assets/textures/wood_diffuse.png");
        Assert.assertTrue(new File("hp/assets/textures/wood_diffuse.dds").exists());
        Texture texture = TextureFactory.getInstance().getTexture("hp/assets/textures/wood_diffuse.dds");
    }

	@Test
	public void loadsCubeMap() throws IOException {
        CubeMap cubeMap = TextureFactory.getInstance().getCubeMap("hp/assets/textures/wood_diffuse.png");
	}

    @Test
    public void testEqualPercentageWithTolerancePositive() {
        byte[] positive = {Byte.decode("0"), Byte.decode("0"), Byte.decode("0")};
        float expectedPercentage = 0f;
        float actualPercentage = getFailurePercentageWithTolerance(positive, positive, 0.0f);
        Assert.assertTrue("Actual failure percentage" + actualPercentage, actualPercentage <= expectedPercentage);
    }
    @Test
    public void testEqualPercentageWithToleranceNegative() {
        byte[] expected = {Byte.decode("1"), Byte.decode("1"), Byte.decode("1")};
        byte[] actual = {Byte.decode("0"), Byte.decode("0"), Byte.decode("0")};
        float expectedPercentage = 100f;
        float actualPercentage = getFailurePercentageWithTolerance(expected, actual, 0.0f);
        Assert.assertTrue("Actual failure percentage is " + actualPercentage, actualPercentage <= expectedPercentage);
    }
    @Test
    public void testEqualPercentageWithToleranceNegativeAThird() {
        byte[] expected = {Byte.decode("3"), Byte.decode("3"), Byte.decode("1")};
        byte[] actual = {Byte.decode("3"), Byte.decode("3"), Byte.decode("0")};
        float expectedPercentage = 66.666f;
        float actualPercentage = getFailurePercentageWithTolerance(expected, actual, 0.0f);
        Assert.assertTrue("Actual failure percentage is " + actualPercentage, actualPercentage <= expectedPercentage);
    }

    public float getFailurePercentageWithTolerance(byte[] expected, byte[] actual, float toleranceInPercent) {
        if(expected.length != actual.length) { return 100f; }

        int diffCounter = 0;
        for(int i = 0; i < expected.length; i++) {
            int expectedValue = Byte.toUnsignedInt(expected[i]);
            int actualValue = Byte.toUnsignedInt(actual[i]);
            float tolerance = 0.01f*toleranceInPercent;
            if(!isInRange(expectedValue, actualValue, tolerance)) { diffCounter++; }
        }
        float actualFailurePercentage = 100f*((float)diffCounter/(float)expected.length);
        float actualPassedValuesPercentage = 100f - actualFailurePercentage;
        LOGGER.info("Percentage of values within the tolerance is " + actualPassedValuesPercentage);
        return actualFailurePercentage;
    }

    private boolean isInRange(int expectedValue, int actualValue, float toleranceInPercent) {
        return actualValue <= (1+toleranceInPercent) * expectedValue &&
                actualValue >= (1-toleranceInPercent) * expectedValue;
    }

    @Test
    public void testisInRange() {
        Assert.assertTrue(isInRange(10, 9, 0.1f));
        Assert.assertTrue(isInRange(10, 11, 0.1f));
        Assert.assertFalse(isInRange(10, 12, 0.1f));
        Assert.assertFalse(isInRange(10, 8, 0.1f));
    }


    @Test(timeout = 20000L)
    public void testUnloadAndReloadTexture() throws Exception {
        String fileName = "testfolder/stone_normal_streaming_test.dds";
        Assert.assertTrue(new File(fileName).exists());
        Assert.assertFalse(TextureFactory.getInstance().TEXTURES.containsKey(fileName));
        Texture texture = TextureFactory.getInstance().getTexture(fileName);
        while(texture.getUploadState() != UPLOADING) {
            Thread.sleep(20);
        }
        LOGGER.info("Texture uploading");
        while(texture.getUploadState() != UPLOADED) {
            Thread.sleep(20);
        }
        LOGGER.info("Texture uploaded");

//        de.hanno.hpengine.texture.unload();
        Thread.sleep(TextureFactory.TEXTURE_UNLOAD_THRESHOLD_IN_MS + 5);
        texture.setPreventUnload(true);
        Assert.assertEquals(NOT_UPLOADED, (texture.getUploadState()));
        texture.setUsedNow();
        while(texture.getUploadState() != UPLOADED) {
            LOGGER.info("uploadState is " + texture.getUploadState());
            Thread.sleep(20);
        }
        Assert.assertEquals(UPLOADED, texture.getUploadState());

    }

    public static class PathTest {
        @Test
        public void testPathHandlingDDS() {
            String fileName = "hp/assets/textures/wood_diffuse.dds";
            Assert.assertEquals("hp/assets/textures/wood_diffuse.dds", Texture.getFullPathAsDDS(fileName));
        }
        @Test
        public void testPathHandlingBackslashes() {
            String fileName = "hp\\assets\\textures\\wood_diffuse.png";
            Assert.assertEquals("hp\\assets\\textures\\wood_diffuse.dds", Texture.getFullPathAsDDS(fileName));
        }
        @Test
        public void testFilenameHandlingDDS() {
            String fileName = "wood_diffuse.dds";
            Assert.assertEquals("wood_diffuse.dds", Texture.getFullPathAsDDS(fileName));
        }
        @Test
        public void testPathHandlingNonDDS() {
            String fileName = "hp/assets/textures/wood_diffuse.png";
            Assert.assertEquals("hp/assets/textures/wood_diffuse.dds", Texture.getFullPathAsDDS(fileName));
        }
        @Test
        public void testExistingDDS() {
            String fileName = "testfolder/stone_normal.png";
            File fileAsPNG = new File(fileName);
            Assert.assertEquals("testfolder\\stone_normal.png", fileAsPNG.getPath());
            Assert.assertTrue("Missing: " + fileAsPNG.getAbsolutePath(), fileAsPNG.exists());

            File fileAsDDS = new File(Texture.getFullPathAsDDS(fileName));
            String absolutePathToDDS = fileAsDDS.getAbsolutePath();
            Assert.assertEquals("testfolder\\stone_normal.dds", fileAsDDS.getPath());
            Assert.assertTrue("Missing: " + absolutePathToDDS, fileAsDDS.exists());
        }
    }
}
