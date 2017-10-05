import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.component.JavaComponent;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Entity.Instance;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import de.hanno.hpengine.util.ressources.CodeSource;
import org.joml.Vector3f;

import java.io.File;
import java.util.List;
import java.util.Random;

public class Init implements LifeCycle {

    private boolean initialized;

    public void init() {

        try {
            LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/cube.obj"), "cube").execute(Engine.getInstance());
            System.out.println("loaded entities : " + loaded.entities.size());
            for(Entity current : loaded.entities) {
                File componentScriptFile = new File(Engine.getInstance().getDirectoryManager().getGameDir() + "/scripts/SimpleMoveComponent.java");
                current.addComponent(new JavaComponent(new CodeSource(componentScriptFile)));

                for(int clusterIndex = 0; clusterIndex < 5; clusterIndex++) {
                    Entity.Cluster cluster = new Entity.Cluster();
                    Random random = new Random();
                    int count = 5;
                    for(int x = -count; x < count; x++) {
                        for(int y = -count; y < count; y++) {
                            for(int z = -count; z < count; z++) {
                                Transform trafo = new Transform();
                                float randomFloat = random.nextFloat() - 0.5f;
                                trafo.setTranslation(new Vector3f(current.getPosition()).add(new Vector3f(randomFloat*15*x,randomFloat*15*y,randomFloat*15*z)));
                                cluster.add(new Instance(trafo, MaterialFactory.getInstance().getMaterialsAsList().get((x+y+z+3*count)%10)));
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
