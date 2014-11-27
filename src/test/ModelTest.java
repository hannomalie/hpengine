package test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import main.World;
import main.model.Entity;
import main.model.IEntity;
import main.model.Model;
import main.model.OBJLoader;
import main.model.VertexBuffer;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.DirectionalLight;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class ModelTest {
	
	static Renderer renderer;
	
	@BeforeClass
	public static void init() {
		renderer = new DeferredRenderer(new DirectionalLight(true));
	}
	
	@Test
	public void loadsCorrectly() throws Exception {
		List<Model> box = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/cube.obj"));
		IEntity entity = renderer.getEntityFactory().getEntity(box.get(0));
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
		
		Assert.assertTrue(new Vector3f(10,10,10).equals(entity.getPosition()));
		Vector4f[] minMaxWorld = entity.getMinMaxWorld();
		Vector4f min = minMaxWorld[0];
		Vector4f max = minMaxWorld[1];

		Assert.assertEquals(new Vector4f(9.5f, 9.5f, 9.5f, 0), min);
		Assert.assertEquals(new Vector4f(10.5f, 10.5f, 10.5f, 0), max);
	}
	
	@Test
	public void loadsSphereAndTransformsCorrectly() throws Exception {

		List<Model> sphere = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sphere.obj"));
		IEntity entity = renderer.getEntityFactory().getEntity(sphere.get(0));
		
		entity.setPosition(new Vector3f(0, 0, 0));
		
		Vector4f[] minMaxWorld = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector4f(-1f, -1f, -1f, 0), minMaxWorld[0]);
		Assert.assertEquals(new Vector4f(1f, 1f, 1f, 0), minMaxWorld[1]);
		

		entity.setPosition(new Vector3f(1, 0, 0));
		minMaxWorld = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector4f(0, -1f, -1f, 0), minMaxWorld[0]);
		Assert.assertEquals(new Vector4f(2f, 1f, 1f, 0), minMaxWorld[1]);
		
		entity.setScale(2);
		minMaxWorld = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector4f(-1f, -2f, -2f, 0), minMaxWorld[0]);
		Assert.assertEquals(new Vector4f(3f, 2f, 2f, 0), minMaxWorld[1]);
	}
	
	

}
