import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.model.loader.md5.AnimationController;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.transform.SimpleSpatial;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.component.JavaComponent;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Cluster;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.model.Instance;
import de.hanno.hpengine.util.ressources.CodeSource;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class InitCubeClusters implements LifeCycle {

    private boolean initialized;

    int maxDistance = 15;
    int clusterDistance = 10*maxDistance;
    Vector3f[] clusterLocations = {new Vector3f(clusterDistance, 0, clusterDistance),
            new Vector3f(clusterDistance, 0, -clusterDistance),
            new Vector3f(-clusterDistance, 0, -clusterDistance),
            new Vector3f(0, 0, 0),
            new Vector3f(-clusterDistance, 0, clusterDistance)};

    public void init(Engine engine) {

        try {
            LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/cube.obj"), "cube").execute(engine);
            System.out.println("loaded entities : " + loaded.entities.size());
            for(final Entity current : loaded.entities) {
                File componentScriptFile = new File(engine.getDirectoryManager().getGameDir() + "/scripts/SimpleMoveComponent.java");
                current.addComponent(new JavaComponent(new CodeSource(componentScriptFile)));

                for(int clusterIndex = 0; clusterIndex < 5; clusterIndex++) {
                    Cluster cluster = new Cluster();
                    Random random = new Random();
                    int count = 5;
                    for(int x = -count; x < count; x++) {
                        for(int y = -count; y < count; y++) {
                            for(int z = -count; z < count; z++) {
                                Transform trafo = new Transform();
                                float randomFloat = random.nextFloat() - 0.5f;
                                trafo.setTranslation(new Vector3f().add(new Vector3f(clusterLocations[clusterIndex%clusterLocations.length])).add(new Vector3f(randomFloat* maxDistance *x,randomFloat* maxDistance *y,randomFloat* maxDistance *z)));

                                ModelComponent modelComponent = current.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);
                                List<Material> materials = modelComponent == null ? new ArrayList<Material>() : modelComponent.getMaterials();
                                cluster.add(new Instance(, trafo, materials, new AnimationController(0,0), new SimpleSpatial(){
                                    @Override
                                    public Vector3f[] getMinMax() {
                                        return current.getMinMax();
                                    }
                                }));
                            }
                        }
                    }
                    current.addCluster(cluster);
                    System.out.println("Added " + cluster.size());
                }
            }

            engine.getSceneManager().getScene().addAll(loaded.entities);
            Thread.sleep(500);
            initialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public boolean isInitialized() {
        return initialized;
    }
}
