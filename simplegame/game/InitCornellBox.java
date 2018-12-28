import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;

import java.io.File;

public class InitCornellBox implements LifeCycle {

    private boolean initialized;

    @Override public void init(de.hanno.hpengine.engine.backend.EngineContext engine) {

        try {
            {
                LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/cornellbox.obj"), "cornell").execute(engine);
                System.out.println("loaded entities : " + loaded.entities.size());
                engine.getSceneManager().getScene().addAll(loaded.entities);
            }

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
