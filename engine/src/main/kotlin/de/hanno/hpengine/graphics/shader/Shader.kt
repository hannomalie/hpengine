package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.ressources.CodeSource
import de.hanno.hpengine.ressources.Reloadable
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import java.io.IOException
import java.util.logging.Logger
import java.util.regex.Pattern

sealed class Shader(private val programManager: ProgramManager,
                    override var source: CodeSource,
                    val defines: Defines = Defines(),
                    val shaderType: ShaderType
) : Reloadable, de.hanno.hpengine.graphics.shader.api.Shader {
    private val gpuContext = programManager.gpuContext

    override val id = programManager.gpuContext.onGpu { GL20.glCreateShader(shaderType.glShaderType) } // TODO: Abstract createShader in gpuContext

    override fun load() {
        source.load()

        val resultingShaderSource = programManager.run { source.toResultingShaderSource(defines) }

        gpuContext.onGpu {
            GL20.glShaderSource(id, resultingShaderSource)
            GL20.glCompileShader(id)
        }

        val shaderLoadFailed = gpuContext.onGpu {
            val shaderStatus = GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS)
            if (shaderStatus == GL11.GL_FALSE) {
                System.err.println("Could not compile " + shaderType + ": " + source.name)
                var shaderInfoLog = GL20.glGetShaderInfoLog(id, 10000)
                val lines = resultingShaderSource.lines()
                System.err.println("Could not compile " + shaderType + ": " + source.name)
                System.err.println("Problematic line:")
                System.err.println(lines[Regex("""\d\((.*)\)""").find(shaderInfoLog)!!.groups[1]!!.value.toInt()-1])
                shaderInfoLog = replaceLineNumbersWithDynamicLinesAdded(shaderInfoLog, lines.size)
                System.err.println(resultingShaderSource)
                ShaderLoadException(shaderInfoLog)
            } else null
        }

        shaderLoadFailed?.let { throw it }
    }

    override fun reload() = load()
    override fun unload() {
        source.unload()
        GL20.glDeleteShader(id)
    }

    override val name: String
        get() = source.name

    override fun equals(other: Any?): Boolean {
        if(other !is Shader) return false

        return id == other.id && defines == other.defines
    }

    override fun hashCode(): Int {
        var hash = 0
        hash += id.hashCode()
        hash += defines.hashCode()
        return hash
    }
    init {
        load()
    }

    companion object {

        val LOGGER = Logger.getLogger(Shader::class.java.name)

        @Throws(IOException::class)
        fun replaceIncludes(engineDir: EngineDirectory, shaderFileAsText: String, currentNewLineCount: Int): Pair<String, Int> {
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

            return Pair(shaderFileAsText, currentNewLineCount)
        }

        fun replaceLineNumbersWithDynamicLinesAdded(shaderInfoLog: String, newlineCount: Int): String {
            var shaderInfoLog = shaderInfoLog

            val loCPattern = Pattern.compile("\\((\\w+)\\) :")
            val loCMatcher = loCPattern.matcher(shaderInfoLog)

            while (loCMatcher.find()) {
                val oldLineNumber = loCMatcher.group(1)
                val newLineNumber = Integer.parseInt(oldLineNumber) - newlineCount
                val regex = String.format("\\($oldLineNumber\\) :", oldLineNumber).toRegex()
                shaderInfoLog = shaderInfoLog.replace(regex, "(line $newLineNumber) :")
            }

            return shaderInfoLog
        }

    }
}

class VertexShader(programManager: OpenGlProgramManager, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(programManager, shaderSource, defines,
    ShaderType.VertexShader
)
class TesselationControlShader(programManager: OpenGlProgramManager, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(programManager, shaderSource, defines,
    ShaderType.TesselationControlShader
)
class TesselationEvaluationShader(programManager: OpenGlProgramManager, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(programManager, shaderSource, defines,
    ShaderType.TesselationEvaluationShader
)
class GeometryShader(programManager: OpenGlProgramManager, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(programManager, shaderSource, defines,
    ShaderType.GeometryShader
)
class FragmentShader(programManager: OpenGlProgramManager, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(programManager, shaderSource, defines,
    ShaderType.FragmentShader
)

class ComputeShader(programManager: OpenGlProgramManager, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(programManager, shaderSource, defines,
    ShaderType.ComputeShader
)
