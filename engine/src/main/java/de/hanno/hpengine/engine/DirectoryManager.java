package de.hanno.hpengine.engine;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.manager.Manager;
import de.hanno.hpengine.engine.model.material.SimpleMaterial;
import de.hanno.hpengine.engine.model.texture.OpenGlTexture;
import de.hanno.hpengine.engine.scene.SimpleScene;
import de.hanno.hpengine.engine.model.texture.PathBasedOpenGlTexture;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DirectoryManager implements Manager {

    public static final String WORKDIR_NAME = "hp";
    public static final String ASSETDIR_NAME = "hp/assets";
    public static final String GAMEDIR_NAME = "game";
    private final File gameDir;
    private final File gameInitScript;

    public DirectoryManager(String gameDir) {
        this.gameDir = new File(gameDir);
        gameInitScript = this.gameDir.toPath().resolve(Config.getInstance().getInitFileName()).toFile();
    }

    public void initWorkDir() {
        ArrayList<File> dirs = new ArrayList<>();
        dirs.add(new File(WORKDIR_NAME));
        dirs.add(new File(ASSETDIR_NAME));
        dirs.add(new File(OpenGlTexture.directory));
        dirs.add(new File(SimpleMaterial.Companion.getDirectory()));
        dirs.add(new File(Entity.getDirectory()));
        dirs.add(new File(SimpleScene.Companion.getDirectory()));

        dirs.add(new File(GAMEDIR_NAME));

        for (File file : dirs) {
            createIfAbsent(file);
        }
    }

    public String getGameDirName() {
        return GAMEDIR_NAME;
    }
    public File getGameDir() {
        return gameDir;
    }

    public File getGameInitScript() {
        return gameInitScript;
    }

    private boolean createIfAbsent(File folder) {
        if (!folder.exists()) {
            return folder.mkdir();
        }
        return true;
    }

    @Override
    public void clear() {

    }

    @Override
    public void update(float deltaSeconds) {

    }

    @Override
    public void onEntityAdded(@NotNull List<? extends Entity> entities) {

    }

    @Override
    public void afterUpdate(float deltaSeconds) {

    }
}
