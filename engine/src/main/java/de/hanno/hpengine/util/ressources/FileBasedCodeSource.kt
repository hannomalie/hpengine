package de.hanno.hpengine.util.ressources

import org.apache.commons.io.FileUtils.readFileToString
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.io.IOException

sealed class CodeSource: Reloadable {
    abstract val source: String
}
open class StringBasedCodeSource(override val name: String, override val source: String): CodeSource()

class FileBasedCodeSource(val file: File) : CodeSource() {
    init {
        require(file.exists()) { "Cannot load file ${file.path} as it doesn't exist" }
        require(file.isFile) { "Cannot load file ${file.path} as it is not a file" }
    }
    override var source: String = getSourceStringFromFile()
        private set

    val filename = FilenameUtils.getBaseName(file.name)

    override val name: String
        get() = filename

    override fun load() {
        source = getSourceStringFromFile()
    }

    private fun getSourceStringFromFile(): String = try {
        readFileToString(file)
    } catch (e: IOException) {
        System.err.println("Cannot reload shader file, old one is kept ($filename)")
        throw e
    }
}