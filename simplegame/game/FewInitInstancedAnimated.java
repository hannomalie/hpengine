import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Instance;
import de.hanno.hpengine.engine.model.loader.md5.AnimationController;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.transform.*;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class FewInitInstancedAnimated implements LifeCycle {

    private boolean initialized;

    int maxDistance = 475;
    int clusterDistance = 3*maxDistance;
    Vector3f[] clusterLocations = {new Vector3f(clusterDistance, 0, clusterDistance),
            new Vector3f(clusterDistance, 0, -clusterDistance),
            new Vector3f(-clusterDistance, 0, -clusterDistance),
            new Vector3f(0, 0, 0),
            new Vector3f(-clusterDistance, 0, clusterDistance)};

    public void init() {

        try {
            LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/doom3monster/monster.md5mesh"), "cube").execute(Engine.getInstance());
            System.out.println("loaded entities : " + loaded.entities.size());
            for (final Entity current : loaded.entities) {

                final ModelComponent modelComponent = current.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);
                List<Material> materials = modelComponent == null ? new ArrayList<Material>() : modelComponent.getMaterials();

                for(int i = 0; i < 3; i++) {
                    InstanceSpatial spatial = modelComponent.isStatic() ? new InstanceSpatial() : new AnimatedInstanceSpatial();
                    final Transform trafo = new Transform();
                    trafo.rotate(new Vector3f(1, 0, 0), -90);
                    trafo.setTranslation(new Vector3f(100*i, 0, 0));
                    Instance instance = new Instance(current, trafo, materials, new AnimationController(120, 24), spatial);
                    spatial.setInstance(instance);
                    current.addExistingInstance(instance);
                }
            }

            Engine.getInstance().getScene().addAll(loaded.entities);
            Thread.sleep(500);
            initialized = true;
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public boolean isInitialized() {
        return initialized;
    }
}
