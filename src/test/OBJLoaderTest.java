package test;

import main.Face;
import main.util.OBJLoader;
import main.util.Util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.newdawn.slick.opengl.Texture;

public class OBJLoaderTest {
	@BeforeClass
	public static void setupOpenGLContext() throws LWJGLException {
		PixelFormat pixelFormat = new PixelFormat();
		ContextAttribs contextAtrributes = new ContextAttribs(4, 2)
			.withForwardCompatible(true);
//			.withProfileCore(true);
		
		Display.setDisplayMode(new DisplayMode(10, 10));
		Display.setTitle("ForwardRenderer");
		Display.create(pixelFormat, contextAtrributes);
	}

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
	
	@Test
	public void extractFileExtension() {
		String extension = Util.getFileExtension("/assets/textures/stone_diffuse.png");
		Assert.assertEquals("png", extension);
	}

	@Test
	public void loadTextureFromJar() {
		Texture texture = Util.loadTexture("src/assets/textures/stone_diffuse.png");
		Assert.assertEquals(512, texture.getImageHeight());
	}
	
	@Test
	public void loadTextureFromDirecotry() {
		Texture texture = Util.loadTexture("C://default.png");
		Assert.assertEquals(128, texture.getImageHeight());
	}

}
