import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.entity.Entity;

import java.io.File;

public class InitSponza implements LifeCycle {

    private boolean initialized;

    public void init(Engine engine) {

        try {
            LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sponza.obj"), "sponza").execute(engine);
            System.out.println("loaded entities : " + loaded.entities.size());
            for (Entity entity : loaded.entities) {
                entity.init(engine);
            }
            engine.getSceneManager().getScene().addAll(loaded.entities);

            Entity entity = engine.getEntityManager().create();
            entity.addComponent(new Camera(entity));
            engine.getSceneManager().getScene().add(entity);
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
