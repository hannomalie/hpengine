package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.backend.OpenGlBackend
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.shader.Shader.Companion.directory
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.util.ressources.CodeSource
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class OpenGlProgramManager(override val gpuContext: OpenGLContext, private val eventBus: EventBus) : ProgramManager<OpenGlBackend> {

    override fun getProgramFromFileNames(vertexShaderFilename: String, fragmentShaderFileName: String?, defines: Defines): Program {
        val vertexShaderSource = getShaderSource(File(directory + vertexShaderFilename))
        val fragmentShaderSource = fragmentShaderFileName?.run { getShaderSource(File(directory + this)) }

        return getProgram(vertexShaderSource, fragmentShaderSource, null, defines = defines)
    }

    override fun getComputeProgram(computeShaderLocation: String, defines: Defines): ComputeShaderProgram {
        return gpuContext.calculate {
            val program = ComputeShaderProgram(this, getShaderSource(File(directory + computeShaderLocation)), defines)
            LOADED_PROGRAMS.add(program)
            eventBus.register(program)
            program
        }
    }

    override fun getProgram(vertexShaderSource: CodeSource,
                            fragmentShaderSource: CodeSource?,
                            geometryShaderSource: CodeSource?,
                            defines: Defines): Program {

        return gpuContext.calculate {
            val program = Program(this, vertexShaderSource, geometryShaderSource, fragmentShaderSource, defines)
            LOADED_PROGRAMS.add(program)
            eventBus.register(program)
            program
        }
    }

    override fun <SHADERTYPE : Shader> loadShader(type: Class<SHADERTYPE>, shaderSource: CodeSource, defines: Defines): SHADERTYPE {

        var resultingShaderSource = (gpuContext.getOpenGlVersionsDefine()
                + gpuContext.getOpenGlExtensionsDefine()
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

    companion object {

        var LOADED_PROGRAMS: MutableList<AbstractProgram> = CopyOnWriteArrayList()
    }
}
