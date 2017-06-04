package de.hanno.hpengine;

import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.model.*;
import jme3tools.optimize.LodGenerator;
import org.junit.Assert;
import org.junit.Test;
import org.joml.Vector3f;

import java.io.File;

public class MeshTest extends TestWithEngine {

    @Test
    public void loadsPlaneCorrectly() throws Exception {
        Model plane = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/plane.obj"));
        Mesh planeMesh = plane.getMesh(0);
        Assert.assertEquals(4, planeMesh.getFaces().size());

        Assert.assertEquals(2, planeMesh.getFaces().size());
        Assert.assertEquals(6, planeMesh.getIndexBufferValuesArray().length);

        int[] expectedIndexBufferValues = {0,1,2,3,0,2};
        Assert.assertArrayEquals(expectedIndexBufferValues, planeMesh.getIndexBufferValuesArray());

        Entity entity = EntityFactory.getInstance().getEntity("plane", plane);
        ModelComponent modelComponent = entity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);

        LodGenerator lodGenerator = new LodGenerator(modelComponent);
        lodGenerator.bakeLods(LodGenerator.TriangleReductionMethod.PROPORTIONAL, 0.3f);
        Assert.assertEquals(1, modelComponent.getLodLevels().size());
        Assert.assertEquals(6, modelComponent.getLodLevels().get(0).length);
    }

    @Test
    public void calculatesLodsCorrectly() throws Exception {
        Model plane = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/doublePlane.obj"));
        Mesh planeMesh = plane.getMesh(0);
        Assert.assertEquals(6, planeMesh.getFaces().size());

        Assert.assertEquals(4, planeMesh.getFaces().size());
        Assert.assertEquals(12, planeMesh.getIndexBufferValuesArray().length);

        Entity entity = EntityFactory.getInstance().getEntity("plane", plane);
        ModelComponent modelComponent = entity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);

        Assert.assertEquals(2, modelComponent.getLodLevels().size());
        Assert.assertEquals(6, modelComponent.getLodLevels().get(1).length);
        int[] expectedIndexBufferValues = {0,1,2,3,0,2};
        Assert.assertArrayEquals(expectedIndexBufferValues, modelComponent.getLodLevels().get(1));
    }
	

	@Test
	public void loadsSphereAndTransformsCorrectly() throws Exception {

        Model sphere = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj"));
        Entity entity = EntityFactory.getInstance().getEntity("sphere", sphere);
		
		entity.setPosition(new Vector3f(0, 0, 0));
		
		Vector3f[] minMaxWorld = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector3f(-1f, -1f, -1f), minMaxWorld[0]);
		Assert.assertEquals(new Vector3f(1f, 1f, 1f), minMaxWorld[1]);
		

		entity.setPosition(new Vector3f(1, 0, 0));
		minMaxWorld = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector3f(0, -1f, -1f), minMaxWorld[0]);
		Assert.assertEquals(new Vector3f(2f, 1f, 1f), minMaxWorld[1]);

		entity.setScale(2);
		minMaxWorld = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector3f(-1f, -2f, -2f), minMaxWorld[0]);
		Assert.assertEquals(new Vector3f(3f, 2f, 2f), minMaxWorld[1]);

		Vector3f[] minMaxWorldVec3 = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector3f(-1f, -2f, -2f), minMaxWorldVec3[0]);
		Assert.assertEquals(new Vector3f(3f, 2f, 2f), minMaxWorldVec3[1]);

		entity.moveInWorld(new Vector3f(0, 5, 0));
		minMaxWorldVec3 = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector3f(-1f, 3f, -2f), minMaxWorldVec3[0]);
		Assert.assertEquals(new Vector3f(3f, 7f, 2f), minMaxWorldVec3[1]);
	}
	
	

}
