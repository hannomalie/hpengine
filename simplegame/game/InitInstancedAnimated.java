import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Cluster;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Instance;
import de.hanno.hpengine.engine.model.Model;
import de.hanno.hpengine.engine.model.loader.md5.AnimationController;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import de.hanno.hpengine.engine.transform.SimpleSpatial;
import de.hanno.hpengine.engine.transform.Transform;
import org.joml.Vector3f;

import java.io.File;
import java.util.Random;

public class InitInstancedAnimated implements LifeCycle {

    private boolean initialized;

    int maxDistance = 175;
    int clusterDistance = 4*maxDistance;
    Vector3f[] clusterLocations = {new Vector3f(clusterDistance, 0, clusterDistance),
            new Vector3f(clusterDistance, 0, -clusterDistance),
            new Vector3f(-clusterDistance, 0, -clusterDistance),
            new Vector3f(0, 0, 0),
            new Vector3f(-clusterDistance, 0, clusterDistance)};

    public void init() {

        try {
            LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/doom3monster/monster.md5mesh"), "cube").execute(Engine.getInstance());
            System.out.println("loaded entities : " + loaded.entities.size());
            for(final Entity current : loaded.entities) {
//                File componentScriptFile = new File(Engine.getInstance().getDirectoryManager().getGameDir() + "/scripts/SimpleMoveComponent.java");
//                current.addComponent(new JavaComponent(new CodeSource(componentScriptFile)));

                for(int clusterIndex = 0; clusterIndex < 5; clusterIndex++) {
                    Cluster cluster = new Cluster();
                    Random random = new Random();
                    int count = 2;
                    for(int x = -count; x < count; x++) {
                        for(int y = -count; y < count; y++) {
                            for(int z = -count; z < count; z++) {
                                Transform trafo = new Transform();
                                float randomFloat = random.nextFloat() - 0.5f;
                                trafo.setTranslation(new Vector3f().add(new Vector3f(clusterLocations[clusterIndex%clusterLocations.length])).add(new Vector3f(randomFloat* maxDistance *x,randomFloat* maxDistance *y,randomFloat* maxDistance *z)));
                                Material material = current.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getMaterial();//MaterialFactory.getInstance().getMaterialsAsList().get((x + y + z + 3 * count) % 10);
                                cluster.add(new Instance(trafo, material, new AnimationController(120,24 + randomFloat*7), new SimpleSpatial(){
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

            Engine.getInstance().getScene().addAll(loaded.entities);
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
