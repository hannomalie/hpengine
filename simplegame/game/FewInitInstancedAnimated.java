import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.transform.Transform;
import org.joml.Vector3f;

import java.io.File;

public class FewInitInstancedAnimated implements LifeCycle {

    private boolean initialized;

    int maxDistance = 475;
    int clusterDistance = 3*maxDistance;
    Vector3f[] clusterLocations = {new Vector3f(clusterDistance, 0, clusterDistance),
            new Vector3f(clusterDistance, 0, -clusterDistance),
            new Vector3f(-clusterDistance, 0, -clusterDistance),
            new Vector3f(0, 0, 0),
            new Vector3f(-clusterDistance, 0, clusterDistance)};

    public void init(Engine engine) {

        try {
            LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/doom3monster/monster.md5mesh"), "cube").execute(Engine.getInstance());
            System.out.println("loaded entities : " + loaded.entities.size());
            for (final Entity current : loaded.entities) {

                for(int i = 0; i < 300; i++) {
                    final Transform trafo = new Transform();
                    trafo.rotate(new Vector3f(1, 0, 0), -90);
                    trafo.setTranslation(new Vector3f(100*i, 0, 0));
                    Entity.addInstance(current, trafo);
                }
            }

            Engine.getInstance().getSceneManager().getScene().addAll(loaded.entities);

            Engine.getInstance().getSceneManager().getScene().add(new Camera());
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
