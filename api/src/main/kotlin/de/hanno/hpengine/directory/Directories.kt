package de.hanno.hpengine.directory

import org.koin.core.annotation.Single
import java.io.File

@Single
data class Directories(
    val engineDir: EngineDirectory = EngineDirectory(File(ENGINEDIR_NAME)),
    val gameDir: GameDirectory
) {
    companion object {
        const val ENGINEDIR_NAME = "hp"
        const val GAMEDIR_NAME = "game"
    }
}
