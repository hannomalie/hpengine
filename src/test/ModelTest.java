package test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import main.DirectionalLight;
import main.Entity;
import main.ForwardRenderer;
import main.Model;
import main.VertexBuffer;
import main.util.OBJLoader;

import org.junit.Assert;
import org.junit.Test;

public class ModelTest {
	
	@Test
	public void loadsCorrectly() throws IOException {
		ForwardRenderer renderer = new ForwardRenderer(new DirectionalLight(false));
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
	}

}
