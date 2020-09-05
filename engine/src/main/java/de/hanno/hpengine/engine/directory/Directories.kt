package de.hanno.hpengine.engine.directory

import java.io.File

class Directories(engineDir: File = File(WORKDIR_NAME),
                  gameDir: File = File(GAMEDIR_NAME),
                  initFileName: String) {

    constructor(engineDir: String, gameDir: String, initFileName: String): this(File(engineDir), File(gameDir), initFileName)

    val engineDir = EngineDirectory(engineDir)
    val gameDir = GameDirectory(gameDir, gameDir.resolve(initFileName))

    companion object {
        const val WORKDIR_NAME = "hp"
        const val GAMEDIR_NAME = "game"
    }
}
