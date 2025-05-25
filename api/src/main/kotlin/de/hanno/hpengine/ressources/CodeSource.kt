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

    val includedFiles: MutableList<File> = mutableListOf()

    override fun load() {
        source = getSourceStringFromFile()
        includedFiles.clear()
        includedFiles.addAll(source.extractIncludeFiles())
    }

    private fun getSourceStringFromFile(): String = try {
        file.readText()
    } catch (e: IOException) {
        System.err.println("Cannot reload shader file, old one is kept ($filename)")
        throw e
    }

    override fun equals(other: Any?): Boolean = other is FileBasedCodeSource && file.path == other.file.path
    override fun hashCode(): Int = file.path.hashCode()
    fun registerFiles(files: List<File>) {
        includedFiles.addAll(files)
        val distinct = includedFiles.distinct()
        includedFiles.clear()
        includedFiles.addAll(distinct)
    }

    companion object {
        fun File.toCodeSource() = FileBasedCodeSource(this)
        fun Asset.toCodeSource() = resolve().toCodeSource()
    }
}

class WrappedCodeSource(val underlying: FileBasedCodeSource,
                        override val name: String = underlying.name + System.currentTimeMillis().toString(),
                        val replacements: Array<Pair<String, String>>,
                        val fileBasedReplacements: Array<Pair<String, File>>): CodeSource {

    init {
        underlying.registerFiles(fileBasedReplacements.map { it.second })
    }
    override val source: String
        get() {
            var result = underlying.source
            replacements.forEach { (oldValue, newValue) ->
                result = result.replace(oldValue, newValue)
            }
            fileBasedReplacements.forEach { (oldValue, newValue) ->
                result = result.replace(oldValue, newValue.readText())
            }
            return result
        }

    override fun load() {
        underlying.load()
        underlying.includedFiles.addAll(fileBasedReplacements.map { it.second })
    }
    override fun equals(other: Any?): Boolean {
        return other is WrappedCodeSource && other.name == name && replacements.contentEquals(other.replacements)
    }

    override fun hashCode(): Int = name.hashCode() + replacements.contentHashCode()
}

fun FileBasedCodeSource.enhanced(name: String,
                                 replacements: Array<Pair<String, String>> = emptyArray(),
                                 fileBasedReplacements:Array<Pair<String, File>> = emptyArray(),
) = WrappedCodeSource(this, name, replacements, fileBasedReplacements)


fun CodeSource.hasChanged(reference: String): Boolean = reference != source
fun CodeSource.hasChanged(reference: Int): Boolean = reference != source.hashCode()


internal val includeRegex = Regex("//include\\(?(.+)\\)")

fun String.extractIncludeFiles(): List<File> = lines().mapNotNull { line ->
    includeRegex.find(line)?.groupValues?.get(1)?.let { File(it) }
}.distinct()
