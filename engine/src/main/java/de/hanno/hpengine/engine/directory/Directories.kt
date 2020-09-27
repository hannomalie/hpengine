package de.hanno.hpengine.engine.directory

import java.io.File

class Directories(val engineDir: EngineDirectory = EngineDirectory(File(ENGINEDIR_NAME)),
                  val gameDir: GameDirectory) {

    constructor(engineDir: File, gameDir: File): this(EngineDirectory(engineDir), GameDirectory(gameDir, null))
    constructor(engineDir: String, gameDir: String): this(File(engineDir), File(gameDir))

    companion object {
        const val ENGINEDIR_NAME = "hp"
        const val GAMEDIR_NAME = "game"
    }
}
