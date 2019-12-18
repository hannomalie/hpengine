package de.hanno.hpengine.engine.directory

import java.io.File
import java.lang.IllegalStateException


interface Directory {
    val models: File
    val assets: File
    val textures: File
}
open class AbstractDirectory(path: String): File(path) {
    init {
        if (isFile) throw IllegalStateException("$absolutePath is a file, not a directory")
        if(!exists()) {
            mkdirs()
            println("Created game directory $absolutePath")
        }
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