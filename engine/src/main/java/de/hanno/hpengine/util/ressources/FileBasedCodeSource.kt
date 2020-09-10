package de.hanno.hpengine.util.ressources

import de.hanno.hpengine.engine.directory.Asset
import java.io.File
import java.io.IOException

interface CodeSource: Reloadable {
    val source: String
}
class StringBasedCodeSource(override val name: String, override val source: String): CodeSource

class FileBasedCodeSource(val file: File) : CodeSource {
    init {
        require(file.exists()) { "Cannot load file ${file.path} as it doesn't exist" }
        require(file.isFile) { "Cannot load file ${file.path} as it is not a file" }
    }
    override var source: String = getSourceStringFromFile()
        private set

    val filename = file.nameWithoutExtension

    override val name: String = filename

    override fun load() {
        source = getSourceStringFromFile()
    }

    private fun getSourceStringFromFile(): String = try {
        file.readText()
    } catch (e: IOException) {
        System.err.println("Cannot reload shader file, old one is kept ($filename)")
        throw e
    }
    companion object {
        fun File.toCodeSource() = FileBasedCodeSource(this)
        fun Asset.toCodeSource() = resolve().toCodeSource()
    }
}

fun CodeSource.hasChanged(reference: String): Boolean = reference != source