package de.hanno.hpengine.util.ressources

import de.hanno.hpengine.engine.directory.Asset
import java.io.File
import java.io.IOException

interface CodeSource: Reloadable {
    val source: String
}
interface ReloadableCodeSource: CodeSource
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

class WrappedCodeSource(val underlying: FileBasedCodeSource,
                        override val name: String = underlying.name + System.currentTimeMillis().toString(),
                        val enhancer: String.() -> String): CodeSource {

    override val source: String
        get() = underlying.source.enhancer()

    override fun load() = underlying.load()
}

fun FileBasedCodeSource.enhanced(name: String = this.name + System.currentTimeMillis().toString(),
                                 enhancer: String.() -> String) = WrappedCodeSource(this, name, enhancer)

fun CodeSource.hasChanged(reference: String): Boolean = reference != source
fun CodeSource.hasChanged(reference: Int): Boolean = reference != source.hashCode()