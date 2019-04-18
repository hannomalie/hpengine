import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.directory.DirectoryManager;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.EngineConsumer;
import kotlin.io.FilesKt;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class InitSponza implements EngineConsumer {

    private boolean initialized;

    @Override
    public void consume(@NotNull de.hanno.hpengine.engine.Engine engine) {

        try {
            File modelFile = FilesKt.resolve(Config.getInstance().getDirectoryManager().getGameDir(), "assets/models/sponza.obj");
            LoadModelCommand.EntityListResult loaded = new LoadModelCommand(modelFile, "sponza", engine.getScene().getMaterialManager()).execute();
            System.out.println("loaded entities : " + loaded.entities.size());
            for (Entity entity : loaded.entities) {
                entity.init(engine);
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
