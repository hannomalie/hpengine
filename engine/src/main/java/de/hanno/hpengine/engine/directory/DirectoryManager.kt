package de.hanno.hpengine.engine.directory

import de.hanno.hpengine.engine.manager.Manager
import java.io.File

class DirectoryManager constructor(engineDir: String = WORKDIR_NAME,
                                     gameDir: String = GAMEDIR_NAME,
                                     initFileName: String) : Manager {

    val engineDir = EngineDirectory(engineDir)
    val gameDir = GameDirectory(gameDir, File(gameDir).resolve(initFileName))

    private fun createIfAbsent(folder: File): Boolean {
        return if (!folder.exists()) {
            folder.mkdir()
        } else true
    }


    companion object {
        const val WORKDIR_NAME = "hp"
        const val GAMEDIR_NAME = "game"
    }
}
