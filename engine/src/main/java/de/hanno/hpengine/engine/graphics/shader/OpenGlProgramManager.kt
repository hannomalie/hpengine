package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.directory.AbstractDirectory
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.util.ressources.StringBasedCodeSource
import de.hanno.hpengine.util.ressources.WrappedCodeSource
import de.hanno.hpengine.util.ressources.hasChanged
import kotlinx.coroutines.CoroutineScope
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import java.io.IOException
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList

class OpenGlProgramManager(override val gpuContext: OpenGLContext,
                           private val eventBus: EventBus,
                           val config: Config) : ProgramManager<OpenGl> {

    var programsCache: MutableList<AbstractProgram> = CopyOnWriteArrayList()

    override fun getComputeProgram(codeSource: FileBasedCodeSource, defines: Defines): ComputeProgram {
        return gpuContext.invoke {
            val program = ComputeProgram(this, codeSource, defines)
            programsCache.add(program)
            eventBus.register(program)
            program
        }
    }

    override fun getProgram(vertexShaderSource: CodeSource,
                            fragmentShaderSource: CodeSource?,
                            geometryShaderSource: CodeSource?,
                            defines: Defines): Program {

        return gpuContext.invoke {
            Program(this, vertexShaderSource, geometryShaderSource, fragmentShaderSource, defines).apply {
                programsCache.add(this)
                eventBus.register(this)
            }
        }
    }

    var programsSourceCache: WeakHashMap<Shader, String> = WeakHashMap()
    override fun update(deltaSeconds: Float) {
        programsCache.forEach { program ->
            program.shaders.forEach { shader ->
                if(shader.shaderSource is StringBasedCodeSource || shader.shaderSource is WrappedCodeSource) {
                    programsSourceCache.putIfAbsent(shader, shader.shaderSource.source)
                    if(shader.shaderSource.hasChanged(programsSourceCache[shader]!!)) program.reload()
                }
            }
        }
    }

    override fun loadShader(shaderType: Shader.ShaderType, shaderSource: CodeSource, defines: Defines): Int {

        var resultingShaderSource = (gpuContext.getOpenGlVersionsDefine()
                + gpuContext.getOpenGlExtensionsDefine()
                + defines.toString()
                + ShaderDefine.getGlobalDefinesString(config))

        var newlineCount = resultingShaderSource.split("\n".toRegex()).toTypedArray().size - 1

        var actualShaderSource = shaderSource.source

        try {
            val tuple = Shader.replaceIncludes(config.directories.engineDir, actualShaderSource, newlineCount)
            actualShaderSource = tuple.left
            newlineCount = tuple.right
            resultingShaderSource += actualShaderSource
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val shaderId: Int = gpuContext.invoke {
            GL20.glCreateShader(shaderType.glShaderType).also { shaderId ->
                GL20.glShaderSource(shaderId, resultingShaderSource)
                GL20.glCompileShader(shaderId)
            }
        }

        val shaderLoadFailed = gpuContext.invoke {
            val shaderStatus = GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS)
            if (shaderStatus == GL11.GL_FALSE) {
                System.err.println("Could not compile " + shaderType + ": " + shaderSource.name)
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
        gpuContext.exceptionOnError("loadShader: " + shaderType + ": " + shaderSource.name)

        return shaderId
    }
}
