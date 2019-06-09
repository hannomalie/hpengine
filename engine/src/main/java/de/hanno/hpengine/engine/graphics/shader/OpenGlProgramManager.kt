package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.shader.Shader.Companion.directory
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.util.ressources.CodeSource
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class OpenGlProgramManager(override val gpuContext: OpenGLContext,
                           private val eventBus: EventBus,
                           val config: Config) : ProgramManager<OpenGl> {
    init {
//        gpuContext.getExceptionOnError { "OpenGlProgramManager init" }
    }

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
                + ShaderDefine.getGlobalDefinesString(config))

        var newlineCount = resultingShaderSource.split("\n".toRegex()).toTypedArray().size - 1

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

        val shaderId: Int = gpuContext.calculate {
            GL20.glCreateShader(shader.shaderType.glShaderType).also { shaderId ->
                GL20.glShaderSource(shaderId, resultingShaderSource)
                GL20.glCompileShader(shaderId)
            }
        }
        shader.id = shaderId

        shaderSource.resultingShaderSource = resultingShaderSource

        val shaderLoadFailed = gpuContext.calculate {
            val shaderStatus = GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS)
            if (shaderStatus == GL11.GL_FALSE) {
                System.err.println("Could not compile " + type.simpleName + ": " + shaderSource.filename)
                var shaderInfoLog = GL20.glGetShaderInfoLog(shaderId, 10000)
                shaderInfoLog = Shader.replaceLineNumbersWithDynamicLinesAdded(shaderInfoLog, newlineCount)
                System.err.println(shaderInfoLog)
                true
            } else false
        }

        if (shaderLoadFailed) {
            throw Shader.ShaderLoadException(resultingShaderSource)
        }

        Shader.LOGGER.finer(resultingShaderSource)
        gpuContext.getExceptionOnError { "loadShader: " + type.simpleName + ": " + shaderSource.filename }

        return shader
    }

    companion object {

        var LOADED_PROGRAMS: MutableList<AbstractProgram> = CopyOnWriteArrayList()
    }
}
