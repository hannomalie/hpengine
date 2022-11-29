package de.hanno.hpengine.ressources

import de.hanno.hpengine.directory.Asset
import java.io.File
import java.io.IOException

interface CodeSource: Reloadable {
    val source: String
}
data class StringBasedCodeSource(override val name: String, override val source: String): CodeSource

class FileBasedCodeSource(val file: File) : CodeSource {
    init {
        require(file.exists()) { "Cannot load file ${file.path} as it doesn't exist" }
        require(file.isFile) { "Cannot load file ${file.path} as it is not a file" }
    }
    override var source: String = getSourceStringFromFile()
        private set

    private val filename = file.nameWithoutExtension

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

    override fun equals(other: Any?): Boolean = other is FileBasedCodeSource && file.path == other.file.path
    override fun hashCode(): Int = file.path.hashCode()

    companion object {
        fun File.toCodeSource() = FileBasedCodeSource(this)
        fun Asset.toCodeSource() = resolve().toCodeSource()
    }
}

class WrappedCodeSource(val underlying: FileBasedCodeSource,
                        override val name: String = underlying.name + System.currentTimeMillis().toString(),
                        val replacements: Array<Pair<String, String>>): CodeSource {

    override val source: String
        get() {
            var result = underlying.source
            replacements.forEach { (oldValue, newValue) ->
                result = result.replace(oldValue, newValue)
            }
            return result
        }

    override fun load() = underlying.load()
    override fun equals(other: Any?): Boolean {
        return other is WrappedCodeSource && other.name == name && replacements.contentEquals(other.replacements)
    }

    override fun hashCode(): Int = name.hashCode() + replacements.contentHashCode()
}

fun FileBasedCodeSource.enhanced(name: String,
                                 replacements: Array<Pair<String, String>>) = WrappedCodeSource(this, name, replacements)


fun CodeSource.hasChanged(reference: String): Boolean = reference != source
fun CodeSource.hasChanged(reference: Int): Boolean = reference != source.hashCode()