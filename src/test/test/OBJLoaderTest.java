package test;

import engine.World;
import engine.model.Face;
import engine.model.Model;
import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import util.Util;
import util.stopwatch.StopWatch;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class OBJLoaderTest extends TestWithRenderer {

	@Test
	public void parseVertex() {
		Vector3f expected = new Vector3f(0.500f, 0.500f, 0.500f);
		Vector3f vertex = renderer.getOBJLoader().parseVertex("v 0.500 0.500 0.500");
		Assert.assertTrue(vertex.equals(expected));
	}
	@Test
	public void parseTexCoord() {
		Vector2f expected = new Vector2f(1.000f, 0.000f);
		Vector2f vertex =  renderer.getOBJLoader().parseTexCoords("vt 1.000 0.000");
		Assert.assertTrue(vertex.equals(expected));
	}
	@Test
	public void parseNormal() {
		Vector2f expected = new Vector2f(-1.000f, 0.000f);
		Vector2f vertex =  renderer.getOBJLoader().parseTexCoords("vn -1.000 0.000");
		Assert.assertTrue(vertex.equals(expected));
	}
	@Test
	public void parseFace() throws Exception {
		Face expected = new Face(new int[]{1,2,3}, new int[]{1,2,3}, new int[]{1,1,1});
		Face face =  renderer.getOBJLoader().parseFace("f 1/1/1 2/2/1 3/3/1");
		Assert.assertArrayEquals(expected.getVertexIndices(), face.getVertexIndices());
		Assert.assertArrayEquals(expected.getTextureCoordinateIndices(), face.getTextureCoordinateIndices());
		Assert.assertArrayEquals(expected.getNormalIndices(), face.getNormalIndices());
	}
	
	@Test
	public void extractFileExtension() {
		String extension = Util.getFileExtension("/assets/textures/stone_diffuse.png");
		Assert.assertEquals("png", extension);
	}

	@Test
	public void loadTextureFromJar() throws IOException {
		texture.Texture texture = renderer.getTextureFactory().getTexture("src/assets/textures/stone_diffuse.png");
		Assert.assertEquals(512, texture.getImageHeight());
	}
	
	@Test
	public void loadTextureFromDirecotry() throws IOException {
		texture.Texture texture = renderer.getTextureFactory().getTexture("C://default.png");
		Assert.assertEquals(1, texture.getImageHeight());
	}
	
	@Test
	public void loadSponzaTest() throws Exception {
		StopWatch.ACTIVE = true;
		StopWatch.getInstance().start("Sponza loading");
		List<Model> sponza = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sponza.obj"));
		StopWatch.getInstance().stopAndPrintMS();
		StopWatch.ACTIVE = false;
	}

}