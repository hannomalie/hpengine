package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.shader.ShaderType
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.ressources.CodeSource
import de.hanno.hpengine.ressources.Reloadable
import java.io.IOException
import java.util.logging.Logger
import java.util.regex.Pattern

sealed class Shader(
    var source: CodeSource,
    val defines: Defines = Defines(),
    val shaderType: ShaderType,
    val gpuContext: GpuContext,
) {
    val id: Int = gpuContext.createShaderId(shaderType)
    val name: String get() = source.name

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

    companion object {

        @Throws(IOException::class)
        fun replaceIncludes(engineDir: EngineDirectory, shaderFileAsText: String, currentNewLineCount: Int): Pair<String, Int> {
            var shaderFileAsText = shaderFileAsText
            var currentNewLineCount = currentNewLineCount

            val includePattern = Pattern.compile("//include\\((.*)\\)")
            val includeMatcher = includePattern.matcher(shaderFileAsText)

            while (includeMatcher.find()) {
                val filename = includeMatcher.group(1)
                val fileToInclude = engineDir.resolve("shaders/$filename").readText()
                currentNewLineCount += fileToInclude.countNewLines()
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

class VertexShader(gpuContext: GpuContext, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(
    shaderSource, defines, ShaderType.VertexShader, gpuContext
)
class TesselationControlShader(gpuContext: GpuContext, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(
    shaderSource, defines, ShaderType.TesselationControlShader, gpuContext
)
class TesselationEvaluationShader(gpuContext: GpuContext, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(
    shaderSource, defines, ShaderType.TesselationEvaluationShader, gpuContext
)
class GeometryShader(gpuContext: GpuContext, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(
    shaderSource, defines, ShaderType.GeometryShader, gpuContext
)
class FragmentShader(gpuContext: GpuContext, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(
    shaderSource, defines, ShaderType.FragmentShader, gpuContext
)

class ComputeShader(gpuContext: GpuContext, shaderSource: CodeSource, defines: Defines = Defines()) : Shader(
    shaderSource, defines, ShaderType.ComputeShader, gpuContext
)

private  fun String.countNewLines(): Int {
    return lines().size
//    TODO: Figure out if the above is the same as the below code
//    val findStr = "\n"
//    return content.split(findStr).dropLastWhile { it.isEmpty() }.toTypedArray().size - 1
}
