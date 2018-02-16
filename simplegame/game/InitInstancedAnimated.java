import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.model.loader.md5.AnimationController;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.transform.*;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class InitInstancedAnimated implements LifeCycle {

    private boolean initialized;

    int maxDistance = 475;
    int clusterDistance = 3 * maxDistance;
    Vector3f[] clusterLocations = {new Vector3f(clusterDistance, 0, clusterDistance),
            new Vector3f(clusterDistance, 0, -clusterDistance),
            new Vector3f(-clusterDistance, 0, -clusterDistance),
            new Vector3f(0, 0, 0),
            new Vector3f(-clusterDistance, 0, clusterDistance)};

    public void init(Engine engine) {

        try {
            Engine.getInstance().getSceneManager().getScene().add(new Camera());
            loadLotsOfInstances("/assets/models/doom3monster/monster.md5mesh", 1, "hellknight");
            Thread.sleep(500);
//            loadLotsOfInstances("/assets/models/cube.obj", 100, "cube");
//            Thread.sleep(500);
            initialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    protected void loadLotsOfInstances(String assetPath, int scale, String name) {
        LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + assetPath), name).execute(Engine.getInstance());
        System.out.println("loaded entities : " + loaded.entities.size());
        for (final Entity current : loaded.entities) {
//                File componentScriptFile = new File(Engine.getInstance().getDirectoryManager().getGameDir() + "/scripts/SimpleMoveComponent.java");
//                current.addComponent(new JavaComponent(new CodeSource(componentScriptFile)));

            for (int clusterIndex = 0; clusterIndex < 5; clusterIndex++) {
                Cluster cluster = new Cluster();
                Random random = new Random();
                int count = 8;
                for (int x = -count; x < count; x++) {
                    for (int y = -count; y < count; y++) {
                        for (int z = -count; z < count; z++) {
                            final Transform trafo = new Transform();
                            trafo.scale(scale);
                            float randomFloat = random.nextFloat() - 0.5f;
                            trafo.rotate(new Vector3f(1, 0, 0), -90);
                            trafo.rotate(new Vector3f(0, 0, 1), (int) (random.nextFloat() * 360f));
                            trafo.setTranslation(new Vector3f().add(new Vector3f(clusterLocations[clusterIndex % clusterLocations.length])).add(new Vector3f(randomFloat * maxDistance * x, 0.001f * randomFloat, randomFloat * maxDistance * z)));

                            final ModelComponent modelComponent = current.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);
                            List<Material> materials = modelComponent == null ? new ArrayList<Material>() : modelComponent.getMaterials();
                            InstanceSpatial spatial = modelComponent.isStatic() ? new InstanceSpatial() : new AnimatedInstanceSpatial();
                            Instance instance = new Instance(current, trafo, materials, new AnimationController(120, 24 + 10 * randomFloat), spatial);
                            spatial.setInstance(instance);

                            cluster.add(instance);
                        }
                    }
                }
                current.addCluster(cluster);
                System.out.println("Added " + cluster.size());
            }
        }

        Engine.getInstance().getSceneManager().getScene().addAll(loaded.entities);
    }

    public boolean isInitialized() {
        return initialized;
    }
}
