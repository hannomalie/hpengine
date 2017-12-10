package de.hanno.hpengine;

import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.transform.AABB;
import jme3tools.optimize.LodGenerator;
import org.junit.Assert;
import org.junit.Test;
import org.joml.Vector3f;

import java.io.File;

public class MeshTest extends TestWithEngine {

    @Test
    public void loadsPlaneCorrectly() throws Exception {
        StaticModel plane = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/plane.obj"));
        Mesh planeMesh = plane.getMesh(0);
        Assert.assertEquals(4, planeMesh.getFaces().size());

        Assert.assertEquals(2, planeMesh.getFaces().size());
        Assert.assertEquals(6, planeMesh.getIndexBufferValuesArray().length);

        int[] expectedIndexBufferValues = {0,1,2,3,0,2};
        Assert.assertArrayEquals(expectedIndexBufferValues, planeMesh.getIndexBufferValuesArray());
    }

    @Test
    public void calculatesLodsCorrectly() throws Exception {
        StaticModel plane = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/doublePlane.obj"));
        Mesh planeMesh = plane.getMesh(0);
        Assert.assertEquals(6, planeMesh.getFaces().size());

        Assert.assertEquals(4, planeMesh.getFaces().size());
        Assert.assertEquals(12, planeMesh.getIndexBufferValuesArray().length);
    }
	

	@Test
	public void loadsSphereAndTransformsCorrectly() throws Exception {

        StaticModel sphere = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj"));
        Entity entity = EntityFactory.getInstance().getEntity("sphere", sphere);

        entity.setTranslation(new Vector3f(0, 0, 0));

        AABB minMaxWorld = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector3f(-1f, -1f, -1f), minMaxWorld.getMin());
		Assert.assertEquals(new Vector3f(1f, 1f, 1f), minMaxWorld.getMax());


        entity.setTranslation(new Vector3f(1, 0, 0));
        minMaxWorld = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector3f(0, -1f, -1f), minMaxWorld.getMin());
		Assert.assertEquals(new Vector3f(2f, 1f, 1f), minMaxWorld.getMax());

		entity.scale(2);
		minMaxWorld = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector3f(-1f, -2f, -2f), minMaxWorld.getMin());
		Assert.assertEquals(new Vector3f(3f, 2f, 2f), minMaxWorld.getMax());

		AABB minMaxWorldVec3 = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector3f(-1f, -2f, -2f), minMaxWorldVec3.getMin());
		Assert.assertEquals(new Vector3f(3f, 2f, 2f), minMaxWorldVec3.getMax());

        entity.translateLocal(new Vector3f(0, 5, 0));
        minMaxWorldVec3 = entity.getMinMaxWorld();
		Assert.assertEquals(new Vector3f(-1f, 3f, -2f), minMaxWorldVec3.getMin());
		Assert.assertEquals(new Vector3f(3f, 7f, 2f), minMaxWorldVec3.getMax());
	}
	
	

}
