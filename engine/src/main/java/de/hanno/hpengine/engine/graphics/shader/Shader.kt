package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.util.TypedTuple
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.Reloadable
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL32
import org.lwjgl.opengl.GL43
import java.io.IOException
import java.util.logging.Logger
import java.util.regex.Pattern

sealed class Shader(var shaderSource: CodeSource,
                    val id: Int,
                    val shaderType: ShaderType) : Reloadable {

    constructor(programManager: OpenGlProgramManager, sourceCode: CodeSource, defines: Defines = Defines(), shaderType: ShaderType):
            this(sourceCode, programManager.loadShader(shaderType, sourceCode, defines), shaderType)

    enum class ShaderType constructor(val glShaderType: Int) {
        VertexShader(GL20.GL_VERTEX_SHADER),
        FragmentShader(GL20.GL_FRAGMENT_SHADER),
        GeometryShader(GL32.GL_GEOMETRY_SHADER),
        ComputeShader(GL43.GL_COMPUTE_SHADER)
    }

    class ShaderLoadException(private val shaderSource: String) : RuntimeException() {

        override fun toString(): String {
            val source = shaderSource.lines().mapIndexed { index, it -> "${index+1}:$it\n" }.fold("", { a, b -> a+b})
            return source
        }

    }

    override fun load() = shaderSource.load()
    override fun unload() = shaderSource.unload()
    override val name: String
        get() = shaderSource.name

    companion object {

        val LOGGER = Logger.getLogger(Shader::class.java.name)

        @Throws(IOException::class)
        fun replaceIncludes(engineDir: EngineDirectory, shaderFileAsText: String, currentNewLineCount: Int): TypedTuple<String, Int> {
            var shaderFileAsText = shaderFileAsText
            var currentNewLineCount = currentNewLineCount

            val includePattern = Pattern.compile("//include\\((.*)\\)")
            val includeMatcher = includePattern.matcher(shaderFileAsText)

            while (includeMatcher.find()) {
                val filename = includeMatcher.group(1)
                val fileToInclude = engineDir.resolve("shaders/$filename").readText()
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

    }
}

class VertexShader(programManager: OpenGlProgramManager, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(programManager, shaderSource, defines, ShaderType.VertexShader)
class FragmentShader(programManager: OpenGlProgramManager, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(programManager, shaderSource, defines, ShaderType.FragmentShader)
class GeometryShader(programManager: OpenGlProgramManager, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(programManager, shaderSource, defines, ShaderType.GeometryShader)
class ComputeShader(programManager: OpenGlProgramManager, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(programManager, shaderSource, defines, ShaderType.ComputeShader)
