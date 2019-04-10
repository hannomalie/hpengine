package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.shader.Shader.Companion.directory
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.util.ressources.CodeSource
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class ProgramManager(val gpuContext: GpuContext, private val eventBus: EventBus) : Manager {

    val blurProgram = getProgram(getShaderSource(File(directory + "passthrough_vertex.glsl")), getShaderSource(File(directory + "blur_fragment.glsl")))
    val bilateralBlurProgram = getProgram(getShaderSource(File(directory + "passthrough_vertex.glsl")), getShaderSource(File(directory + "blur_bilateral_fragment.glsl")))

    @JvmOverloads
    fun getProgramFromFileNames(vertexShaderFilename: String, fragmentShaderFileName: String? = null, defines: Defines = Defines()): Program {
        val vertexShaderSource = getShaderSource(File(directory + vertexShaderFilename))
        val fragmentShaderSource = fragmentShaderFileName?.run { getShaderSource(File(directory + this)) }

        return getProgram(vertexShaderSource, fragmentShaderSource, defines = defines)
    }

    @JvmOverloads
    fun getComputeProgram(computeShaderLocation: String, defines: Defines = Defines()): ComputeShaderProgram {
        return gpuContext.calculate {
            val program = ComputeShaderProgram(this, getShaderSource(File(directory + computeShaderLocation)), defines)
            LOADED_PROGRAMS.add(program)
            eventBus.register(program)
            program
        }
    }

    @JvmOverloads
    fun getProgram(vertexShaderSource: CodeSource?,
                   fragmentShaderSource: CodeSource? = null,
                   geometryShaderSource: CodeSource? = null,
                   defines: Defines = Defines()): Program {

        return gpuContext.calculate {
            val program = Program(this, vertexShaderSource, geometryShaderSource, fragmentShaderSource, defines)
            LOADED_PROGRAMS.add(program)
            eventBus.register(program)
            program
        }
    }

    @JvmOverloads
    fun <SHADERTYPE : Shader> loadShader(type: Class<SHADERTYPE>, shaderSource: CodeSource, defines: Defines = Defines()): SHADERTYPE {

        var resultingShaderSource = (getOpenGlVersionDefine()
                + getOpenGlExtensionsDefine()
                + defines.toString()
                + ShaderDefine.getGlobalDefinesString() + "\n")

        val findStr = "\n"
        var newlineCount = resultingShaderSource.split(findStr.toRegex()).toTypedArray().size - 1

        var actualShaderSource = shaderSource.source

        try {
            val tuple = Shader.replaceIncludes(actualShaderSource, newlineCount)
            actualShaderSource = tuple.left
            newlineCount = tuple.right
            resultingShaderSource += actualShaderSource
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val shader: SHADERTYPE = type.newInstance().apply {
                this.shaderSource = shaderSource
            }

        val shaderID = IntArray(1)
        val finalResultingShaderSource = resultingShaderSource
        gpuContext.execute {
            shaderID[0] = GL20.glCreateShader(shader.shaderType.glShaderType)
            shader.id = shaderID[0]
            GL20.glShaderSource(shaderID[0], finalResultingShaderSource)
            GL20.glCompileShader(shaderID[0])
        }

        shaderSource.resultingShaderSource = resultingShaderSource

        val shaderLoadFailed = BooleanArray(1)
        val finalNewlineCount = newlineCount
        gpuContext.execute {
            if (GL20.glGetShaderi(shaderID[0], GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("Could not compile " + type.simpleName + ": " + shaderSource.filename)
                var shaderInfoLog = GL20.glGetShaderInfoLog(shaderID[0], 10000)
                shaderInfoLog = Shader.replaceLineNumbersWithDynamicLinesAdded(shaderInfoLog, finalNewlineCount)
                System.err.println(shaderInfoLog)
                shaderLoadFailed[0] = true
            }
        }

        if (shaderLoadFailed[0]) {
            throw Shader.ShaderLoadException(shaderSource)
        }

        Shader.LOGGER.finer(resultingShaderSource)
        GpuContext.exitOnGLError { "loadShader: " + type.simpleName + ": " + shaderSource.filename }

        return shader
    }

    fun getOpenGlExtensionsDefine() = "#extension GL_NV_gpu_shader5 : enable\n#extension GL_ARB_bindless_texture : enable\n"

    fun getOpenGlVersionDefine() = "#version 430 core\n"

    companion object {

        var LOADED_PROGRAMS: MutableList<AbstractProgram> = CopyOnWriteArrayList()
    }
}
