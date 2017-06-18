import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Entity;

import java.io.File;
import java.util.List;

public class Init implements LifeCycle {

    private boolean initialized;

    public void init() {

        try {
            {
                LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/cornellbox.obj"), "cornell").execute(Engine.getInstance());
                System.out.println("loaded entities : " + loaded.entities.size());
                Engine.getInstance().getScene().addAll(loaded.entities);
            }
            {
                LoadModelCommand.EntityListResult loaded = new LoadModelCommand(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj"), "sphere").execute(Engine.getInstance());
                List<Entity> entities = loaded.entities;
                System.out.println("loaded entities : " + entities.size());
                Engine.getInstance().getScene().addAll(entities);
                Engine.getInstance().getScene().getEntities().get(0).addChildRelationship(entities.get(0));
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
