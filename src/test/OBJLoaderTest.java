package test;

import main.Face;
import main.util.OBJLoader;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;

public class OBJLoaderTest {

	@Test
	public void parseVertex() {
		Vector3f expected = new Vector3f(0.500f, 0.500f, 0.500f);
		Vector3f vertex = OBJLoader.parseVertex("v 0.500 0.500 0.500");
		Assert.assertTrue(vertex.equals(expected));
	}
	@Test
	public void parseTexCoord() {
		Vector2f expected = new Vector2f(1.000f, 0.000f);
		Vector2f vertex = OBJLoader.parseTexCoords("vt 1.000 0.000");
		Assert.assertTrue(vertex.equals(expected));
	}
	@Test
	public void parseNormal() {
		Vector2f expected = new Vector2f(-1.000f, 0.000f);
		Vector2f vertex = OBJLoader.parseTexCoords("vn -1.000 0.000");
		Assert.assertTrue(vertex.equals(expected));
	}
	@Test
	public void parseFace() {
		Face expected = new Face(new int[]{1,2,3}, new int[]{1,2,3}, new int[]{1,1,1});
		Face face = OBJLoader.parseFace("f 1/1/1 2/2/1 3/3/1");
		Assert.assertArrayEquals(expected.getVertexIndices(), face.getVertexIndices());
		Assert.assertArrayEquals(expected.getTextureCoordinateIndices(), face.getTextureCoordinateIndices());
		Assert.assertArrayEquals(expected.getNormalIndices(), face.getNormalIndices());
	}

}
