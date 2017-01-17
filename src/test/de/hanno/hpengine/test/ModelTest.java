package de.hanno.hpengine.test;

import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.*;
import jme3tools.optimize.LodGenerator;
import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.io.File;
import java.util.List;

public class ModelTest extends TestWithEngine {

    @Test
    public void loadsPlaneCorrectly() throws Exception {
        List<Model> plane = new OBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/plane.obj"));
        Model planeModel = plane.get(0);
        Assert.assertEquals(4, planeModel.getVertices().size());
        Assert.assertEquals(4, planeModel.getNormals().size());
        Assert.assertEquals(4, planeModel.getTexCoords().size());

        Assert.assertEquals(2, planeModel.getFaces().size());
        Assert.assertEquals(6, planeModel.getIndexBufferValuesArray().length);

        int[] expectedIndexBufferValues = {0,1,2,3,0,2};
        Assert.assertArrayEquals(expectedIndexBufferValues, planeModel.getIndexBufferValuesArray());

        Entity entity = EntityFactory.getInstance().getEntity(planeModel);
        ModelComponent modelComponent = entity.getComponent(ModelComponent.class);

        float[] expectedVerticesValues = new float[planeModel.getVertices().size() * 3];
        for(int i = 0; i < planeModel.getVertices().size(); i++) {
            Vector3f vertex = planeModel.getVertices().get(expectedIndexBufferValues[i]);
            expectedVerticesValues[i*3] = vertex.x;
            expectedVerticesValues[i*3+1] = vertex.y;
            expectedVerticesValues[i*3+2] = vertex.z;
        }

//        float[] temp = entity.getComponent(ModelComponent.class).floatArray;

        LodGenerator lodGenerator = new LodGenerator(modelComponent);
        lodGenerator.bakeLods(LodGenerator.TriangleReductionMethod.PROPORTIONAL, 0.3f);
        Assert.assertEquals(1, modelComponent.getLodLevels().size());
        Assert.assertEquals(6, modelComponent.getLodLevels().get(0).length);
    }

    @Test
    public void calculatesLodsCorrectly() throws Exception {
        List<Model> plane = new OBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/doublePlane.obj"));
        Model planeModel = plane.get(0);
        Assert.assertEquals(6, planeModel.getVertices().size());
        Assert.assertEquals(6, planeModel.getNormals().size());
        Assert.assertEquals(6, planeModel.getTexCoords().size());

        Assert.assertEquals(4, planeModel.getFaces().size());
        Assert.assertEquals(12, planeModel.getIndexBufferValuesArray().length);

        Entity entity = EntityFactory.getInstance().getEntity(planeModel);
        ModelComponent modelComponent = entity.getComponent(ModelComponent.class);

        Assert.assertEquals(2, modelComponent.getLodLevels().size());
        Assert.assertEquals(6, modelComponent.getLodLevels().get(1).length);
        int[] expectedIndexBufferValues = {0,1,2,3,0,2};
        Assert.assertArrayEquals(expectedIndexBufferValues, modelComponent.getLodLevels().get(1));
    }
	

	@Test
	public void loadsSphereAndTransformsCorrectly() throws Exception {

        List<Model> sphere = new OBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/sphere.obj"));
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

		Vector3f[] minMaxWorldVec3 = entity.getMinMaxWorldVec3();
		Assert.assertEquals(new Vector3f(-1f, -2f, -2f), minMaxWorldVec3[0]);
		Assert.assertEquals(new Vector3f(3f, 2f, 2f), minMaxWorldVec3[1]);

		entity.moveInWorld(new Vector3f(0, 5, 0));
		minMaxWorldVec3 = entity.getMinMaxWorldVec3();
		Assert.assertEquals(new Vector3f(-1f, 3f, -2f), minMaxWorldVec3[0]);
		Assert.assertEquals(new Vector3f(3f, 7f, 2f), minMaxWorldVec3[1]);
	}
	
	

}
