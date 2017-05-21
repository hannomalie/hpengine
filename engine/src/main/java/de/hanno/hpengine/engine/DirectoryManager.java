package de.hanno.hpengine.engine;

import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.scene.Scene;
import de.hanno.hpengine.texture.Texture;

import java.io.File;
import java.util.ArrayList;

public class DirectoryManager {

    public static final String WORKDIR_NAME = "hp";
    public static final String ASSETDIR_NAME = "hp/assets";
    public static final String GAMEDIR_NAME = "game";
    private final File gameDir;
    private final File gameInitScript;

    public DirectoryManager(String gameDir) {
        this.gameDir = new File(gameDir);
        gameInitScript = this.gameDir.toPath().resolve("Init.java").toFile();
    }

    public void initWorkDir() {
        ArrayList<File> dirs = new ArrayList<>();
        dirs.add(new File(WORKDIR_NAME));
        dirs.add(new File(ASSETDIR_NAME));
        dirs.add(new File(Texture.getDirectory()));
        dirs.add(new File(Material.getDirectory()));
        dirs.add(new File(Entity.getDirectory()));
        dirs.add(new File(Scene.getDirectory()));

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
}
