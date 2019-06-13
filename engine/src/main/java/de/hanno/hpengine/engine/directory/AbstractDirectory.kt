package de.hanno.hpengine.engine.directory

import java.io.File


interface Directory {
    val models: File
    val assets: File
    val textures: File
}
open class AbstractDirectory(path: String): File(path) {
    init {
        require(isDirectory) { "Given path is not a directory: $path" }
    }
}

class EngineDirectory(path: String): AbstractDirectory(path) {
    val shaders = resolve("shaders")
    val assets = resolve("assets")
    val models = assets.resolve("models")
    val textures = assets.resolve("textures")

}
class GameDirectory(path: String, val initScript: File? = null): AbstractDirectory(path) {
    val assets = resolve("assets")
    val models = assets.resolve("models")
    val textures = assets.resolve("textures")
    val scripts = resolve("scripts")
    val java = resolve("java")
}