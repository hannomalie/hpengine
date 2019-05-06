package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.directory.DirectoryManager
import de.hanno.hpengine.util.TypedTuple
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.Reloadable
import org.apache.commons.io.FileUtils
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL32
import org.lwjgl.opengl.GL43
import java.io.File
import java.io.IOException
import java.util.logging.Logger
import java.util.regex.Pattern

interface Shader : Reloadable {
    var shaderSource: CodeSource
    var id: Int

    val shaderType: OpenGLShader

    enum class OpenGLShader constructor(val glShaderType: Int) {
        VertexShader(GL20.GL_VERTEX_SHADER),
        FragmentShader(GL20.GL_FRAGMENT_SHADER),
        GeometryShader(GL32.GL_GEOMETRY_SHADER),
        ComputeShader(GL43.GL_COMPUTE_SHADER)
    }

    class ShaderLoadException(private val shaderSource: String) : RuntimeException() {

        override fun toString(): String {
            val source = shaderSource.lines().mapIndexed { index, it -> "$index:$it\n" }.fold("", {a,b -> a+b})
            return source
        }

    }

    companion object {

        val LOGGER = Logger.getLogger(Shader::class.java.name)

        @Throws(IOException::class)
        fun replaceIncludes(shaderFileAsText: String, currentNewLineCount: Int): TypedTuple<String, Int> {
            var shaderFileAsText = shaderFileAsText
            var currentNewLineCount = currentNewLineCount

            val includePattern = Pattern.compile("//include\\((.*)\\)")
            val includeMatcher = includePattern.matcher(shaderFileAsText)

            while (includeMatcher.find()) {
                val filename = includeMatcher.group(1)
                val fileToInclude = FileUtils.readFileToString(File(directory + filename))
                currentNewLineCount += Util.countNewLines(fileToInclude)
                shaderFileAsText = shaderFileAsText.replace(String.format("//include\\(%s\\)", filename).toRegex(), fileToInclude)
            }

            return TypedTuple(shaderFileAsText, currentNewLineCount)
        }

        fun replaceLineNumbersWithDynamicLinesAdded(shaderInfoLog: String, newlineCount: Int): String {
            var shaderInfoLog = shaderInfoLog

            val loCPattern = Pattern.compile("\\((\\w+)\\) :")
            val loCMatcher = loCPattern.matcher(shaderInfoLog)

            while (loCMatcher.find()) {
                val oldLineNumber = loCMatcher.group(1)
                val newLineNumber = Integer.parseInt(oldLineNumber) - newlineCount
                val regex = String.format("\\($oldLineNumber\\) :", oldLineNumber).toRegex()
                shaderInfoLog = shaderInfoLog.replace(regex, "(ln $newLineNumber) :")
            }

            return shaderInfoLog
        }

        const val directory: String = DirectoryManager.WORKDIR_NAME + "/assets/shaders/"
    }
}

fun getShaderSource(file: File): CodeSource = if (file.exists()) {
        CodeSource(file)
    } else {
        throw IllegalStateException("File ${file.absolutePath} doesn't exist")
    }

fun getShaderSource(shaderSource: String): CodeSource? {
    return if ("" == shaderSource) {
        null
    } else CodeSource(shaderSource)
}