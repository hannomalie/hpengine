import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Entity;

import java.io.File;
import java.util.List;

public class InitSponza implements LifeCycle {

    private boolean initialized;

    public void init() {

        try {
            LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sponza.obj"), "sponza").execute(Engine.getInstance());
            System.out.println("loaded entities : " + loaded.entities.size());
            Engine.getInstance().getScene().addAll(loaded.entities);

            Engine.getInstance().getScene().add(new Camera());
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