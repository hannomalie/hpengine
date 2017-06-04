package de.hanno.hpengine;

import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.model.Face;
import de.hanno.hpengine.engine.model.Model;
import de.hanno.hpengine.engine.model.OBJLoader;
import de.hanno.hpengine.engine.model.material.Material;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.joml.Vector2f;
import org.joml.Vector3f;
import de.hanno.hpengine.engine.model.texture.TextureFactory;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.stopwatch.StopWatch;

import java.io.File;
import java.io.IOException;

public class OBJLoaderTest extends TestWithEngine {

	@Test
	public void parseVertex() {
		Vector3f expected = new Vector3f(0.500f, 0.500f, 0.500f);
        Vector3f vertex = new OBJLoader().parseVertex("v 0.500 0.500 0.500");
		Assert.assertTrue(vertex.equals(expected));
	}
	@Test
	public void parseTexCoord() {
		Vector2f expected = new Vector2f(1.000f, 0.000f);
        Vector2f vertex =  new OBJLoader().parseTexCoords("vt 1.000 0.000");
		Assert.assertTrue(vertex.equals(expected));
	}
	@Test
	public void parseNormal() {
		Vector2f expected = new Vector2f(-1.000f, 0.000f);
        Vector2f vertex =  new OBJLoader().parseTexCoords("vn -1.000 0.000");
		Assert.assertTrue(vertex.equals(expected));
	}
	@Test
	public void parseFace() throws Exception {
		Face expected = new Face(new int[]{1,2,3}, new int[]{1,2,3}, new int[]{1,1,1});
        Face face =  new OBJLoader().parseFace("f 1/1/1 2/2/1 3/3/1");
		Assert.assertArrayEquals(expected.getVertices(), face.getVertices());
		Assert.assertArrayEquals(expected.getTextureCoordinateIndices(), face.getTextureCoordinateIndices());
		Assert.assertArrayEquals(expected.getNormalIndices(), face.getNormalIndices());
	}
	
	@Test
	public void extractFileExtension() {
		String extension = Util.getFileExtension("/assets/textures/stone_diffuse.png");
		Assert.assertEquals("png", extension);
	}

    @Ignore
	@Test
	public void loadTextureFromDirecotry() throws IOException {
        de.hanno.hpengine.engine.model.texture.Texture texture = TextureFactory.getInstance().getTexture("C://default.png");
		Assert.assertEquals(1, texture.getHeight());
	}

    @Test
    public void loadsMaterial() throws Exception {
        Model sibenik = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sibenik.obj"));
        Material material = sibenik.getMesh(0).getMaterial();

        Assert.assertEquals("rozeta", material.getName());
        Assert.assertEquals("hp/assets/models/textures/KAMEN-stup", material.getMaterialInfo().maps.get(Material.MAP.DIFFUSE).getName());
        Assert.assertEquals(1, material.getTextures().size());
    }

	@Test
	public void loadSponzaTest() throws Exception {
		StopWatch.ACTIVE = true;
		StopWatch.getInstance().start("Sponza loading");
		Model sponza = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sponza.obj"));
		StopWatch.getInstance().stopAndPrintMS();
		StopWatch.ACTIVE = false;
	}

	@Test
	public void loadCornellBoxTest() throws Exception {
		Model cornellBox = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/cornellbox.obj"));
		Assert.assertEquals(9, cornellBox.getMeshes().size());
	}

}
