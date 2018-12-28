import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;

import java.io.File;

public class InitSibenik implements LifeCycle {

    private boolean initialized;

    @Override public void init(de.hanno.hpengine.engine.backend.EngineContext engine) {

        try {
            LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sibenik.obj"), "sibenik").execute(engine);
            System.out.println("loaded entities : " + loaded.entities.size());
            for (Entity entity : loaded.entities) {
                entity.init(engine);
                entity.scale(4);
            }
            engine.getSceneManager().getScene().addAll(loaded.entities);

//            Entity entity = engine.getSceneManager().getSimpleScene().getEntityManager().create();
//            entity.addComponent(new Camera(entity));
//            engine.getSceneManager().getSimpleScene().add(entity);
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
