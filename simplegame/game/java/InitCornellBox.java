import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import de.hanno.hpengine.engine.lifecycle.EngineConsumer;
import kotlin.io.FilesKt;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static de.hanno.hpengine.engine.config.Config.getInstance;

public class InitCornellBox implements EngineConsumer {

    private boolean initialized;

    @Override
    public void consume(@NotNull de.hanno.hpengine.engine.Engine engine) {

        try {
            {
                File file = FilesKt.resolve(getInstance().getDirectoryManager().getGameDir(), "assets/models/cornellbox.obj");
                LoadModelCommand.EntityListResult loaded = new LoadModelCommand(file, "cornell", engine.getScene().getMaterialManager()).execute();
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
