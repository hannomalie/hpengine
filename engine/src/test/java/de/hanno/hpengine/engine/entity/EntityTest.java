package de.hanno.hpengine.engine.entity;

import de.hanno.hpengine.TestWithEngine;
import de.hanno.hpengine.engine.directory.DirectoryManager;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.instancing.ClustersComponent;
import de.hanno.hpengine.engine.instancing.ClustersComponentSystem;
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
    public void loadParented() throws Exception {
        StaticModel model = new OBJLoader().loadTexturedModel(engine.getScene().getMaterialManager(), new File(DirectoryManager.engineDir + "/assets/meshes/cornellbox.obj"));
        Entity entity = engine.getSceneManager().getScene().getEntityManager().create("xxx");
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
        StaticModel model = new OBJLoader().loadTexturedModel(engine.getScene().getMaterialManager(), new File(DirectoryManager.engineDir + "/assets/meshes/sphere.obj"));
        Entity parentEntity = engine.getSceneManager().getScene().getEntityManager().create("parent");
        parentEntity.setSelected(true);
        parentEntity.setTranslation(new Vector3f(2,2,2));
        engine.getSceneManager().getScene().add(parentEntity);

        Assert.assertTrue(engine.getSceneManager().getScene().getEntities().contains(parentEntity));
        Assert.assertFalse(parentEntity.hasChildren());
        Assert.assertTrue(parentEntity.getName().equals("parent"));

        Entity childEntity = engine.getSceneManager().getScene().getEntityManager().create("child");
        childEntity.setTranslation(new Vector3f(2,2,2));
        childEntity.setParent(parentEntity);
        engine.getSceneManager().getScene().add(childEntity);

        Assert.assertTrue(engine.getSceneManager().getScene().getEntities().contains(childEntity));
        Assert.assertTrue(parentEntity.hasChildren());
        Assert.assertTrue(childEntity.getName().equals("child"));

        Assert.assertTrue(childEntity.getPosition().equals(new Vector3f(4,4,4)));

        Transform instanceTransform = new SimpleTransform();
        instanceTransform.setTranslation(new Vector3f(15,15,15));
        ClustersComponent clustersComponent = parentEntity.getOrAddComponent(ClustersComponent.class, () -> engine.getScene().getComponentSystems().get(ClustersComponentSystem.class).create(parentEntity));

        ClustersComponent.addInstance(parentEntity, clustersComponent, instanceTransform, parentEntity.spatial);

        Assert.assertTrue(instanceTransform.getPosition().equals(new Vector3f(15,15,15)));

        Entity secondEntity = engine.getSceneManager().getScene().getEntityManager().create("second");
        engine.getSceneManager().getScene().add(secondEntity);
        Assert.assertEquals(0, parentEntity.getComponent(ModelComponent.class).getEntityBufferIndex());
        Assert.assertEquals(4, secondEntity.getComponent(ModelComponent.class).getEntityBufferIndex());

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

//        managerContext.getScene().getComponentSystems().get(ModelComponentSystem.class).getBufferEntitiesActionRef().request(managerContext.getRenderManager().getDrawCycle().get());
        FloatBuffer floatValues = engine.getRenderManager().getRenderState().getCurrentReadState().getEntitiesBuffer().getBuffer().asFloatBuffer();
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