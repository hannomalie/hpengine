package de.hanno.hpengine;

import de.hanno.hpengine.engine.SimpleTransform;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.*;
import org.junit.Assert;
import org.junit.Test;
import org.joml.Vector3f;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.List;

public class EntityTest extends TestWithEngine {

	@Test
	public void writeAndRead() throws Exception {
        Entity entity = EntityFactory.getInstance().getEntity("default", new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj")));

		String filename = "default.hpentity";

		Assert.assertTrue(Entity.write(entity, filename));

        Entity loadedEntity = EntityFactory.getInstance().read(filename);
		Assert.assertTrue(entity.equals(loadedEntity));

	}
    @Test
    public void loadParented() throws Exception {
        StaticModel model = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/meshes/cornellbox.obj"));
        Entity entity = EntityFactory.getInstance().getEntity("xxx", model);
        engine.getScene().add(entity);

        Assert.assertTrue(engine.getScene().getEntities().contains(entity));
        Assert.assertTrue(entity.hasChildren());
        Assert.assertTrue(entity.getName().equals("xxx"));
        List<Mesh> meshes = model.getMeshes();
        for(Mesh mesh : meshes) {
            boolean containsEntity = false;
            for (Entity child : entity.getChildren()) {
                if(child.getName().equals(mesh.getName())) { containsEntity = true; }
                Assert.assertTrue(engine.getScene().getEntities().contains(child));
            }
            Assert.assertTrue(containsEntity);
        }
    }

    @Test
    public void testInstanceBuffering() throws Exception {
        StaticModel model = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/meshes/sphere.obj"));
        Entity parentEntity = EntityFactory.getInstance().getEntity("parent", model);
        parentEntity.setSelected(true);
        parentEntity.setTranslation(new Vector3f(2,2,2));
        engine.getScene().add(parentEntity);

        Assert.assertTrue(engine.getScene().getEntities().contains(parentEntity));
        Assert.assertFalse(parentEntity.hasChildren());
        Assert.assertTrue(parentEntity.getName().equals("parent"));

        Entity childEntity = EntityFactory.getInstance().getEntity("child", model);
        childEntity.setTranslation(new Vector3f(2,2,2));
        childEntity.setParent(parentEntity);
        engine.getScene().add(childEntity);

        Assert.assertTrue(engine.getScene().getEntities().contains(childEntity));
        Assert.assertTrue(parentEntity.hasChildren());
        Assert.assertTrue(childEntity.getName().equals("child"));

        Assert.assertTrue(childEntity.getPosition().equals(new Vector3f(4,4,4)));

        Transform instanceTransform = new SimpleTransform();
        instanceTransform.setTranslation(new Vector3f(15,15,15));
        parentEntity.addInstance(instanceTransform);

        Assert.assertTrue(instanceTransform.getPosition().equals(new Vector3f(15,15,15)));

        Entity secondEntity = EntityFactory.getInstance().getEntity("second", model);
        engine.getScene().add(secondEntity);
        Assert.assertEquals(0, Engine.getInstance().getScene().getEntityBufferIndex(parentEntity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY)));
        Assert.assertEquals(4, Engine.getInstance().getScene().getEntityBufferIndex(secondEntity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY)));

//        TODO: Reimplement this
//        {
//            double[] parentValues = parentEntity.putToBuffer();
//            Assert.assertEquals(20 * 2, parentValues.length); // 2 instances times 20 values per entity
//            Assert.assertTrue(parentValues[12] == 2);
//            Assert.assertTrue(parentValues[13] == 2);
//            Assert.assertTrue(parentValues[14] == 2);
//            Assert.assertTrue(parentValues[32] == 15);
//            Assert.assertTrue(parentValues[33] == 15);
//            Assert.assertTrue(parentValues[34] == 15);
//        }
//        {
//            double[] childValues = childEntity.putToBuffer();
//            Assert.assertEquals(20 * 2, childValues.length); // 2 instances times 20 values per entity
//            Assert.assertTrue(childValues[12] == 4);
//            Assert.assertTrue(childValues[13] == 4);
//            Assert.assertTrue(childValues[14] == 4);
//            Assert.assertTrue(childValues[32] == 15);
//            Assert.assertTrue(childValues[33] == 15);
//            Assert.assertTrue(childValues[34] == 15);
//        }


        engine.getRenderState().getCurrentReadState().bufferEntites(Engine.getInstance().getScene().getEntities());
        FloatBuffer floatValues = engine.getRenderState().getCurrentReadState().getEntitiesBuffer().getBuffer().asFloatBuffer();
        float[] floatValuesArray = new float[floatValues.capacity()];
        floatValues.get(floatValuesArray);

        Assert.assertEquals(2, floatValuesArray[12], 0.00001);
        Assert.assertEquals(2, floatValuesArray[13], 0.00001);
        Assert.assertEquals(2, floatValuesArray[14], 0.00001);
        Assert.assertEquals(1, floatValuesArray[15], 0.00001);
        Assert.assertEquals(15, floatValuesArray[32], 0.00001);
        Assert.assertEquals(15, floatValuesArray[33], 0.00001);
        Assert.assertEquals(15, floatValuesArray[34], 0.00001);

        Assert.assertEquals(4, floatValuesArray[52], 0.00001);
        Assert.assertEquals(4, floatValuesArray[53], 0.00001);
        Assert.assertEquals(4, floatValuesArray[54], 0.00001);

        Assert.assertEquals(15, floatValuesArray[72], 0.00001);
        Assert.assertEquals(15, floatValuesArray[73], 0.00001);
        Assert.assertEquals(15, floatValuesArray[74], 0.00001);


    }
}
