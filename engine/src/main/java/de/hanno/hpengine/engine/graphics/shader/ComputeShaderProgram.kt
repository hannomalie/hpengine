package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.graphics.renderer.GLU
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.Reloadable
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL20.glGetProgramInfoLog
import org.lwjgl.opengl.GL43
import java.util.StringJoiner
import java.util.logging.Logger

class ComputeShaderProgram @JvmOverloads constructor(
        private val programManager: OpenGlProgramManager,
        val computeShaderSource: CodeSource, defines: Defines = Defines()) : AbstractProgram(programManager.gpuContext.createProgramId()), Reloadable {
    private var computeShader: ComputeShader? = null

    init {
        this.defines = defines

        load()
        create()
    }

    override fun load() {
        clearUniforms()
        computeShader = ComputeShader.load(programManager, computeShaderSource, defines)
        printIfError("ComputeShader load " + computeShaderSource.name)
        LOGGER.info("Loaded computeshader " + computeShaderSource.name)
        printIfError("Create program " + computeShaderSource.name)
        attachShader(computeShader!!)
        printIfError("Attach shader " + computeShaderSource.name)
        GL20.glLinkProgram(id)
        printIfError("Link program " + computeShaderSource.name)
        GL20.glValidateProgram(id)
        printIfError("Validate program " + computeShaderSource.name)

        if (GL20.glGetProgrami(getId(), GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.err.println("Could not link shader: " + computeShaderSource.filename)
            System.err.println(GL20.glGetProgramInfoLog(id, 10000))
        }

        printIfError("ComputeShader load ")
    }

    private fun printIfError(text: String): Boolean {
        val error = GL11.glGetError()
        val isError = error != GL11.GL_NO_ERROR
        if (isError) {
            LOGGER.severe(text + " " + GLU.gluErrorString(error))
            LOGGER.info(glGetProgramInfoLog(id))
        }

        return isError
    }

    private fun attachShader(shader: Shader) {
        GL20.glAttachShader(getId(), shader.id)
    }

    private fun detachShader(shader: Shader) {
        GL20.glDetachShader(getId(), shader.id)
    }

    fun dispatchCompute(num_groups_x: Int, num_groups_y: Int, num_groups_z: Int) {
        GL43.glDispatchCompute(num_groups_x, num_groups_y, num_groups_z)
        //		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
    }

    override fun unload() {
        GL20.glUseProgram(0)
        GL20.glDeleteProgram(id)
    }

    override fun reload() {
        val self = this

        val result = programManager.gpuContext.calculate {
            detachShader(computeShader!!)
            try {
                computeShader!!.reload()
                self.load()
                true
            } catch (e: Shader.ShaderLoadException) {
                false
            }
        }
        if (result == java.lang.Boolean.TRUE) {
            LOGGER.info("Program reloaded")
        } else {
            LOGGER.severe("Program not reloaded")
        }
    }

    override fun getName(): String {
        return StringJoiner(", ").add(computeShaderSource!!.filename)
                .toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ComputeShaderProgram) {
            return false
        }

        return this.computeShaderSource == other.computeShaderSource && this.defines.isEmpty()
    }

    override fun hashCode(): Int {
        var hash = 0
        hash += computeShaderSource?.hashCode() ?: 0
        hash += defines.hashCode()
        return hash
    }

    companion object {
        private val LOGGER = Logger.getLogger(ComputeShaderProgram::class.java.name)
    }
}