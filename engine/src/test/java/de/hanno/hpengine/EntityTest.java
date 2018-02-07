package de.hanno.hpengine;

import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.transform.SimpleTransform;
import de.hanno.hpengine.engine.transform.Transform;
import org.joml.Vector3f;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.List;

public class EntityTest extends TestWithEngine {

	@Test
	public void writeAndRead() throws Exception {
        EntityFactory entityFactory = engine.getEntityFactory();
        Entity entity = entityFactory.getEntity("default", new OBJLoader().loadTexturedModel(engine.getMaterialFactory(), new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj")));

		String filename = "default.hpentity";

		Assert.assertTrue(Entity.write(entity, filename));

        Entity loadedEntity = entityFactory.read(filename);
		Assert.assertTrue(entity.equals(loadedEntity));

	}
    @Test
    public void loadParented() throws Exception {
        StaticModel model = new OBJLoader().loadTexturedModel(engine.getMaterialFactory(), new File(DirectoryManager.WORKDIR_NAME + "/assets/meshes/cornellbox.obj"));
        Entity entity = engine.getEntityFactory().getEntity("xxx", model);
        engine.getSceneManager().getScene().add(entity);

        Assert.assertTrue(engine.getSceneManager().getScene().getEntities().contains(entity));
        Assert.assertTrue(entity.hasChildren());
        Assert.assertTrue(entity.getName().equals("xxx"));
        List<Mesh> meshes = model.getMeshes();
        for(Mesh mesh : meshes) {
            boolean containsEntity = false;
            for (Entity child : entity.getChildren()) {
                if(child.getName().equals(mesh.getName())) { containsEntity = true; }
                Assert.assertTrue(engine.getSceneManager().getScene().getEntities().contains(child));
            }
            Assert.assertTrue(containsEntity);
        }
    }

    @Test
    public void testInstanceBuffering() throws Exception {
        StaticModel model = new OBJLoader().loadTexturedModel(engine.getMaterialFactory(), new File(DirectoryManager.WORKDIR_NAME + "/assets/meshes/sphere.obj"));
        Entity parentEntity = engine.getEntityFactory().getEntity("parent", model);
        parentEntity.setSelected(true);
        parentEntity.setTranslation(new Vector3f(2,2,2));
        engine.getSceneManager().getScene().add(parentEntity);

        Assert.assertTrue(engine.getSceneManager().getScene().getEntities().contains(parentEntity));
        Assert.assertFalse(parentEntity.hasChildren());
        Assert.assertTrue(parentEntity.getName().equals("parent"));

        Entity childEntity = engine.getEntityFactory().getEntity("child", model);
        childEntity.setTranslation(new Vector3f(2,2,2));
        childEntity.setParent(parentEntity);
        engine.getSceneManager().getScene().add(childEntity);

        Assert.assertTrue(engine.getSceneManager().getScene().getEntities().contains(childEntity));
        Assert.assertTrue(parentEntity.hasChildren());
        Assert.assertTrue(childEntity.getName().equals("child"));

        Assert.assertTrue(childEntity.getPosition().equals(new Vector3f(4,4,4)));

        Transform instanceTransform = new SimpleTransform();
        instanceTransform.setTranslation(new Vector3f(15,15,15));
        parentEntity.addInstance(instanceTransform);

        Assert.assertTrue(instanceTransform.getPosition().equals(new Vector3f(15,15,15)));

        Entity secondEntity = engine.getEntityFactory().getEntity("second", model);
        engine.getSceneManager().getScene().add(secondEntity);
        Assert.assertEquals(0, engine.getSceneManager().getScene().getEntityBufferIndex(parentEntity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY)));
        Assert.assertEquals(4, engine.getSceneManager().getScene().getEntityBufferIndex(secondEntity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY)));

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


        engine.getRenderSystem().getRenderState().getCurrentReadState().bufferEntities(engine.getSceneManager().getScene().getEntities());
        FloatBuffer floatValues = engine.getRenderSystem().getRenderState().getCurrentReadState().getEntitiesBuffer().getBuffer().asFloatBuffer();
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
