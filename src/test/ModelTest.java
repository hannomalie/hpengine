package test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import main.DeferredRenderer;
import main.Entity;
import main.ForwardRenderer;
import main.Model;
import main.Renderer;
import main.Spotlight;
import main.VertexBuffer;
import main.util.OBJLoader;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class ModelTest {
	
	@Test
	public void loadsCorrectly() throws IOException {
		Renderer renderer = new DeferredRenderer(new Spotlight(false));
		List<Model> box = OBJLoader.loadTexturedModel(new File("C:\\cube.obj"));
		Entity entity = new Entity(renderer, box.get(0));
		VertexBuffer buffer = entity.getVertexBuffer();
		int verticesCount = buffer.getVerticesCount();
		// Korrekter Vertices count
		Assert.assertEquals(36, verticesCount);
		
		float[] vertexData = buffer.getVertexData();
		// Korrekter Element count
		Assert.assertEquals(36*buffer.totalElementsPerVertex(), vertexData.length);
		
		float[] actuals = new float[16];
		for (int i = 0; i < 16; i++) {
			actuals[i] = vertexData[i];
		}
		
		float[] expecteds = new float[]{
				-0.5f, 0.5f, -0.5f,
				0f, 1f,
				0f, 1f, 0f,
				-0.5f, 0.5f, 0.5f,
				0f, 0f,
				0f, 1f, 0f
		};
		// Korrekte Werte eingelesen
		Assert.assertArrayEquals(expecteds, actuals, 0);
		
		entity.move(new Vector3f(10,10,10));
		entity.update(1);
		
		Assert.assertTrue(new Vector3f(10,10,10).equals(entity.getPosition()));
		Vector4f[] minMaxWorld = entity.getMinMaxWorld();
		Vector4f min = minMaxWorld[0];
		Vector4f max = minMaxWorld[1];

		Assert.assertEquals(new Vector4f(9.5f, 9.5f, 9.5f, 0), min);
		Assert.assertEquals(new Vector4f(10.5f, 10.5f, 10.5f, 0), max);
	}

}
