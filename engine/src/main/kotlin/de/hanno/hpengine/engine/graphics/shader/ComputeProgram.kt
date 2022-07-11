package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL42
import org.lwjgl.opengl.GL43
import java.util.logging.Logger

class ComputeProgram @JvmOverloads constructor(
        programManager: OpenGlProgramManager,
        val computeShader: ComputeShader,
        defines: Defines = Defines()) : AbstractProgram<Uniforms>(programManager.gpuContext.createProgramId(), defines, Uniforms.Empty) {

    constructor(programManager: OpenGlProgramManager,
                computeShaderSource: FileBasedCodeSource,
                defines: Defines = Defines()): this(programManager, ComputeShader(programManager, computeShaderSource, defines), defines)

    private val gpuContext = programManager.gpuContext
    override var shaders: List<Shader> = listOf(computeShader)

    init {
        load()
        createFileListeners()
    }

    override fun load() {
        shaders.forEach { attach(it) }

        linkProgram()
        validateProgram()

        registerUniforms()
        gpuContext.backend.gpuContext.exceptionOnError()

        createFileListeners()
    }

    private fun attach(shader: Shader) {
        GL20.glAttachShader(id, shader.id)
    }

    private fun detach(shader: Shader) {
        GL20.glDetachShader(id, shader.id)
    }

    fun dispatchCompute(num_groups_x: Int, num_groups_y: Int, num_groups_z: Int) {
        GL43.glDispatchCompute(num_groups_x, num_groups_y, num_groups_z)
        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS)
    }

    override fun unload() {
        GL20.glUseProgram(0)
        GL20.glDeleteProgram(id)
    }

    override fun reload() = try {
        gpuContext.invoke {
            detach(computeShader)
            computeShader.reload()
            load()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    override val name: String = computeShader.name

    override fun equals(other: Any?): Boolean {
        if (other !is ComputeProgram) {
            return false
        }

        return this.computeShader == other.computeShader && this.defines == other.defines
    }

    override fun hashCode(): Int {
        var hash = 0
        hash += computeShader.hashCode()
        hash += defines.hashCode()
        return hash
    }

    companion object {
        private val LOGGER = Logger.getLogger(ComputeProgram::class.java.name)
    }
}
