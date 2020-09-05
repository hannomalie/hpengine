package de.hanno.hpengine.engine.directory

import java.io.File
import java.lang.IllegalStateException


interface Directory {
    val models: File
    val assets: File
    val textures: File
}
open class AbstractDirectory(val baseDir: File) {
    init {
        require(!baseDir.isFile) { "${baseDir.path} is a file, not a directory" }
//        require(baseDir.exists()) { "Used baseDir for game doesn't exist: ${baseDir.path}" }
    }

    fun resolve(path: String) = baseDir.resolve(path)
    fun resolve(file: File) = baseDir.resolve(file)
}

class EngineDirectory(baseDir: File): AbstractDirectory(baseDir) {
    val shaders = resolve("shaders")
    val assets = resolve("assets")
    val models = assets.resolve("models")
    val textures = assets.resolve("textures")

}
class GameDirectory(baseDir: File, val initScript: File? = null): AbstractDirectory(baseDir) {
    val assets = resolve("assets")
    val models = assets.resolve("models")
    val textures = assets.resolve("textures")
    val scripts = resolve("scripts")
    val java = resolve("java")
}