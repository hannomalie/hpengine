package de.hanno.hpengine.engine.directory

import java.io.File

class Directories(val engineDir: EngineDirectory,
                  val gameDir: GameDirectory) {

    constructor(engineDir: File, gameDir: File, initFileName: String): this(EngineDirectory(engineDir), GameDirectory(gameDir, gameDir.resolve(initFileName)))
    constructor(engineDir: String, gameDir: String, initFileName: String): this(File(engineDir), File(gameDir), initFileName)

    companion object {
        const val ENGINEDIR_NAME = "hp"
        const val GAMEDIR_NAME = "game"
    }
}
