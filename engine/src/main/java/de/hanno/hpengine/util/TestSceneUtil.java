package de.hanno.hpengine.util;

import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.component.PhysicsComponent;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.graphics.light.LightManager;
import de.hanno.hpengine.engine.graphics.light.PointLight;
import de.hanno.hpengine.engine.graphics.renderer.GpuContext;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.entity.EntityManager;
import de.hanno.hpengine.engine.model.StaticModel;
import de.hanno.hpengine.engine.model.OBJLoader;
import de.hanno.hpengine.engine.model.material.MaterialManager;
import de.hanno.hpengine.engine.physics.PhysicsManager;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.model.material.MaterialInfo;
import de.hanno.hpengine.engine.scene.Scene;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TestSceneUtil {
    public static List<Entity> loadTestScene(MaterialManager materialManager, PhysicsManager physicsManager, EntityManager entityManager, LightManager lightManager, Scene scene) {
        List<Entity> entities = new ArrayList<>();
        int entityCount = 3;

        GpuContext.exitOnGLError("loadTestScene");

        try {
//            StaticMesh skyBox = new OBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/skybox.obj")).get(0);
//            Entity skyBoxEntity = EntityContainer.getInstance().getEntity(new Vector3f(), skyBox);
//            skyBoxEntity.setScale(100);
//            entities.add(skyBoxEntity);

            StaticModel sphere = new OBJLoader().loadTexturedModel(materialManager, new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj"));

            for (int i = 0; i < entityCount; i++) {
                for (int j = 0; j < entityCount; j++) {
                    for (int k = 0; k < entityCount; k++) {

                        MaterialInfo materialInfo = new MaterialInfo().setName("Default" + i + "_" + j + "_" + k)
                                .setDiffuse(new Vector3f(1, 1, 1))
                                .setRoughness((float) i / entityCount)
                                .setMetallic((float) j / entityCount)
                                .setDiffuse(new Vector3f((float) k / entityCount, 0, 0))
                                .setAmbient(1);
                        materialInfo.setName("Default_" + i + "_" + j);
                        Material mat = materialManager.getMaterial(materialInfo);
                        mat.setDiffuse(new Vector3f((float)i/entityCount, 0,0));
                        mat.setMetallic((float)j/entityCount);
                        mat.setRoughness((float)k/entityCount);

                        try {
                            Vector3f position = new Vector3f(i * 20, k * 10, -j * 20);
                            Entity entity = entityManager.create(position, "Entity_" + System.currentTimeMillis());
                            entity.addComponent(new ModelComponent(entity, sphere));
                            PointLight pointLight = lightManager.getPointLight(10);
                            pointLight.setTranslation(new Vector3f(i * 19, k * 15, -j * 19));
                            scene.addPointLight(pointLight);
//							Vector3f scale = new Vector3f(0.5f, 0.5f, 0.5f);
//							scale.scale(new Random().nextFloat()*14);
//							entity.setScale(scale);
//
                            PhysicsComponent physicsComponent = physicsManager.addBallPhysicsComponent(entity);
                            entity.addComponent(physicsComponent);
//							physicsComponent.getRigidBody().applyCentralImpulse(new javax.vecmath.Vector3f(10*new Random().nextFloat(), 10*new Random().nextFloat(), 10*new Random().nextFloat()));
//							physicsComponent.getRigidBody().applyTorqueImpulse(new javax.vecmath.Vector3f(0, 100*new Random().nextFloat(), 0));

                            entities.add(entity);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

//			StopWatch.getInstance().start("Load Sponza");
//			List<StaticMesh> sponza = renderer.getOBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/sponza.obj"));
//			for (StaticMesh model : sponza) {
////				model.setMaterials(mirror);
////				if(model.getMaterials().getName().contains("fabric")) {
////					model.setMaterials(mirror);
////				}
//				Entity entity = getEntityManager().getEntity(new Vector3f(0,-21f,0), model);
////				physicsManager.addMeshPhysicsComponent(entity, 0);
//				Vector3f scale = new Vector3f(3.1f, 3.1f, 3.1f);
//				entity.setScale(scale);
//				entities.add(entity);
//			}
//			List<StaticMesh> skyBox = renderer.getOBJLoader().loadTexturedModel(new File(Engine.WORKDIR_NAME + "/assets/models/skybox.obj"));
//			for (StaticMesh model : skyBox) {
//				Entity entity = getEntityManager().getEntity(new Vector3f(0,0,0), model.getName(), model, renderer.getMaterialManager().get("mirror"));
//				Vector3f scale = new Vector3f(3000, 3000f, 3000f);
//				entity.setScale(scale);
//				entities.add(entity);
//			}
//			StopWatch.getInstance().stopAndPrintMS();

            for(Entity entity : entities) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return entities;
        }
    }
}
