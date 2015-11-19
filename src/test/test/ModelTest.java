package test;

import component.ModelComponent;
import engine.AppContext;
import engine.model.*;
import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.io.File;
import java.util.List;

public class ModelTest extends TestWithAppContext {
	
	@Test
	public void loadsCorrectly() throws Exception {
        List<Model> box = new OBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/cube.obj"));
        Entity entity = EntityFactory.getInstance().getEntity(box.get(0));
		VertexBuffer buffer = entity.getComponent(ModelComponent.class).getVertexBuffer();
		int verticesCount = buffer.getVerticesCount();
		// Korrekter Vertices count
		Assert.assertEquals(36, verticesCount);
		
		float[] vertexData = buffer.getValues(DataChannels.POSITION3);
		// Korrekter Element count
		Assert.assertEquals(36*buffer.totalElementsPerVertex(), buffer.getVertexData().length);

		int elementsToCheckCount = 18;
		float[] actuals = new float[elementsToCheckCount];
		for (int i = 0; i < elementsToCheckCount; i++) {
			actuals[i] = vertexData[i];
		}
		
		float[] expecteds = new float[]{
				-0.5f, 0.5f, -0.5f,
				-0.5f, 0.5f, 0.5f,
				0.5f, 0.5f, 0.5f,

				-0.5f, 0.5f, -0.5f,
				0.5f, 0.5f, 0.5f,
				0.5f, 0.5f, -0.5f
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

        List<Model> sphere = new OBJLoader().loadTexturedModel(new File(AppContext.WORKDIR_NAME + "/assets/models/sphere.obj"));
        Entity entity = EntityFactory.getInstance().getEntity(sphere.get(0));
		
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
