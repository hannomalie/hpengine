import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand;
import kotlin.io.FilesKt;

import javax.inject.Inject;
import java.io.File;

public class InitCornellBox {
    public @Inject InitCornellBox(Engine<?> engine) {
        File file = FilesKt.resolve(engine.getConfig().getDirectories().getGameDir(), "assets/models/cornellbox.obj");
        LoadModelCommand.EntityListResult loaded = new LoadModelCommand(file, "cornell", engine.getScene().getMaterialManager(), engine.getDirectories().getGameDir()).execute();
        System.out.println("loaded entities : " + loaded.entities.size());
        engine.getSceneManager().getScene().addAll(loaded.entities);
    }
}
