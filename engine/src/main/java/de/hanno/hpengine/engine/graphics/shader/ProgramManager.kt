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
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList

class ProgramManager(val gpuContext: GpuContext, private val eventBus: EventBus) : Manager {

    val firstpassDefaultVertexshaderSource = getShaderSource(File(directory + "first_pass_vertex.glsl"))
    val firstpassAnimatedDefaultVertexshaderSource = getShaderSource(File(directory + "first_pass_animated_vertex.glsl"))
    val firstpassDefaultFragmentshaderSource = getShaderSource(File(directory + "first_pass_fragment.glsl"))

    val firstpassDefaultProgram = getProgram(firstpassDefaultVertexshaderSource, firstpassDefaultFragmentshaderSource, Defines())
    val firstpassAnimatedDefaultProgram = getProgram(firstpassAnimatedDefaultVertexshaderSource, firstpassDefaultFragmentshaderSource, Defines())

    val highZProgram = getComputeProgram("highZ_compute.glsl")
    val appendDrawcommandsProgram = getProgramFromFileNames("append_drawcommands_vertex.glsl", null, Defines())
    val appendDrawCommandComputeProgram = getComputeProgram("append_drawcommands_compute.glsl", Defines())

    val renderToQuadProgram = getProgram(getShaderSource(File(directory + "passthrough_vertex.glsl")), getShaderSource(File(directory + "simpletexture_fragment.glsl")), Defines())
    val debugFrameProgram = getProgram(getShaderSource(File(directory + "passthrough_vertex.glsl")), getShaderSource(File(directory + "debugframe_fragment.glsl")), Defines())
    val blurProgram = getProgram(getShaderSource(File(directory + "passthrough_vertex.glsl")), getShaderSource(File(directory + "blur_fragment.glsl")), Defines())
    val bilateralBlurProgram = getProgram(getShaderSource(File(directory + "passthrough_vertex.glsl")), getShaderSource(File(directory + "blur_bilateral_fragment.glsl")), Defines())
    val linesProgram = getProgramFromFileNames("mvp_vertex.glsl", "simple_color_fragment.glsl", Defines())

    val defaultFirstpassVertexShader: VertexShader?
        get() {
            try {
                return VertexShader.load(this, firstpassDefaultVertexshaderSource, Defines())
            } catch (e: IOException) {
                e.printStackTrace()
                System.err.println("Default vertex de.hanno.hpengine.shader cannot be loaded...")
                System.exit(-1)
            }

            return null
        }

    fun getProgramFromFileNames(vertexShaderFilename: String, fragmentShaderFileName: String?, defines: Defines): Program {
        val vertexShaderSource = getShaderSource(File(directory + vertexShaderFilename))
        val fragmentShaderSource = getShaderSource(File(directory + fragmentShaderFileName!!))

        return getProgram(vertexShaderSource, fragmentShaderSource, defines)
    }

    fun getProgram(defines: Defines): Program {
        val program = Program(this, firstpassDefaultVertexshaderSource, null, firstpassDefaultFragmentshaderSource, defines)
        LOADED_PROGRAMS.add(program)
        eventBus.register(program)
        return program
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

    fun getProgram(vertexShaderSource: CodeSource?, fragmentShaderSource: CodeSource?, defines: Defines): Program {
        return getProgram(vertexShaderSource, null, fragmentShaderSource, defines)
    }

    fun getProgram(vertexShaderSource: CodeSource?, geometryShaderSource: CodeSource?, fragmentShaderSource: CodeSource?, defines: Defines): Program {
        return gpuContext.calculate {
            val program = Program(this, vertexShaderSource, geometryShaderSource, fragmentShaderSource, defines)
            LOADED_PROGRAMS.add(program)
            eventBus.register(program)
            program
        }
    }

    fun getAppendDrawCommandProgram(): Program? {
        return appendDrawcommandsProgram
    }


    @JvmOverloads
    fun <SHADERTYPE : Shader> loadShader(type: Class<SHADERTYPE>, shaderSource: CodeSource?, defines: Defines = Defines()): SHADERTYPE? {

        if (shaderSource == null) {
            Shader.LOGGER.severe("Shadersource null, returning null de.hanno.hpengine.shader")
            return null
        }

        var resultingShaderSource = ("#version 430 core \n"
                + "#extension GL_NV_gpu_shader5 : enable\n"
                + "#extension GL_ARB_bindless_texture : enable\n"
                + (if (defines.isEmpty()) "" else defines) + "\n"
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

        var shader: SHADERTYPE? = null
        try {
            shader = type.newInstance()
            shader!!.shaderSource = shaderSource
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val finalShader = shader
        val shaderID = IntArray(1)
        val finalShader1 = shader
        val finalResultingShaderSource = resultingShaderSource
        gpuContext.execute {
            shaderID[0] = GL20.glCreateShader(finalShader!!.shaderType.glShaderType)
            finalShader1!!.id = shaderID[0]
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
