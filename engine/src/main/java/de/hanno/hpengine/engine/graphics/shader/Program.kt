package de.hanno.hpengine.engine.graphics.shader

import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.event.GlobalDefineChangedEvent
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.GLU
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.vertexbuffer.DataChannels
import de.hanno.hpengine.log.ConsoleLogger.getLogger
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileMonitor
import de.hanno.hpengine.util.ressources.OnFileChangeListener
import de.hanno.hpengine.util.ressources.Reloadable
import net.engio.mbassy.listener.Handler
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20.GL_LINK_STATUS
import org.lwjgl.opengl.GL20.GL_VALIDATE_STATUS
import org.lwjgl.opengl.GL20.glAttachShader
import org.lwjgl.opengl.GL20.glBindAttribLocation
import org.lwjgl.opengl.GL20.glDeleteProgram
import org.lwjgl.opengl.GL20.glDetachShader
import org.lwjgl.opengl.GL20.glGetProgramInfoLog
import org.lwjgl.opengl.GL20.glGetProgrami
import org.lwjgl.opengl.GL20.glLinkProgram
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.opengl.GL20.glValidateProgram
import java.util.EnumSet
import java.util.HashMap
import java.util.StringJoiner
import java.util.logging.Logger

class Program(private val programManager: OpenGlProgramManager, val vertexShaderSource: CodeSource?, val geometryShaderSource: CodeSource?, val fragmentShaderSource: CodeSource?,
                                    defines: Defines) : AbstractProgram(programManager.gpuContext.createProgramId()), Reloadable {
    private val gpuContext: GpuContext<OpenGl>

    private val needsTextures = true

    private val localDefines = HashMap<String, Any>()

    private var vertexShader: VertexShader? = null
    private var geometryShader: GeometryShader? = null
    private var fragmentShader: FragmentShader? = null

    val defineString: String
        get() {
            val builder = StringBuilder()

            for (shaderDefine in localDefines.entries) {
                builder.append(getDefineTextForObject(shaderDefine))
                builder.append("\n")
            }
            return builder.toString()
        }

    init {
        this.gpuContext = programManager.gpuContext
        this.defines = defines

        create()
        load()
    }

    override fun load() {
        gpuContext.execute {
            clearUniforms()

            vertexShader = programManager.loadShader(VertexShader::class.java, vertexShaderSource!!, defines)

            try {
                if (fragmentShaderSource != null) {
                    fragmentShader = programManager.loadShader(FragmentShader::class.java, fragmentShaderSource, defines)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (geometryShaderSource != null) {
                try {
                    geometryShader = programManager.loadShader(GeometryShader::class.java, geometryShaderSource, defines)
                    gpuContext.backend.gpuContext.exceptionOnError()
                } catch (e: Exception) {
                    LOGGER.severe("Not able to load geometry shader, so what else could be done...")
                }

            }

            gpuContext.backend.gpuContext.exceptionOnError()
            attachShader(vertexShader!!)
            if (fragmentShader != null) attachShader(fragmentShader!!)
            if (geometryShader != null) attachShader(geometryShader!!)
            bindShaderAttributeChannels()
            gpuContext.backend.gpuContext.exceptionOnError()

            linkProgram()
            validateProgram()

            gpuContext.backend.gpuContext.exceptionOnError()

            this.create()
        }
    }

    private fun validateProgram() {
        glValidateProgram(id)
        val validationResult = glGetProgrami(id, GL_VALIDATE_STATUS)
        if (GL11.GL_FALSE == validationResult) {
            System.err.println(glGetProgramInfoLog(id))
            throw IllegalStateException("Program invalid: $name")
        }
    }

    private fun linkProgram() {
        glLinkProgram(id)
        val linkResult = glGetProgrami(id, GL_LINK_STATUS)
        if (GL11.GL_FALSE == linkResult) {
            System.err.println(glGetProgramInfoLog(id))
            throw IllegalStateException("Program not linked: $name")
        }
    }

    private fun attachShader(shader: Shader) {
        glAttachShader(getId(), shader.id)
        gpuContext.backend.gpuContext.exceptionOnError(shader.name)

    }

    private fun detachShader(shader: Shader) {
        glDetachShader(getId(), shader.id)
    }

    private fun printError(text: String): Boolean {
        val error = GL11.glGetError()
        val isError = error != GL11.GL_NO_ERROR
        if (isError) {
            LOGGER.severe(text + " " + GLU.gluErrorString(error))
            LOGGER.info(glGetProgramInfoLog(id))
        }

        return isError
    }

    override fun unload() {
        glDeleteProgram(id)
    }

    override fun reload() = try {
        val result = gpuContext.calculate {
            detachShader(vertexShader!!)
            if (fragmentShader != null) {
                detachShader(fragmentShader!!)
                fragmentShader!!.reload()
            }
            if (geometryShader != null) {
                detachShader(geometryShader!!)
                geometryShader!!.reload()
            }
            vertexShader!!.reload()
            load()
            true
        }

        if (result == java.lang.Boolean.TRUE) {
            LOGGER.info("Program reloaded")
        } else {
            LOGGER.severe("Program not reloaded")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    override fun getName(): String {
        return StringJoiner(", ").add(if (fragmentShaderSource != null) fragmentShaderSource.filename else "").add(vertexShaderSource!!.filename)
                .toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Program || other == null) {
            return false
        }

        val otherProgram = other as Program?

        return if ((this.geometryShaderSource == null && otherProgram!!.geometryShaderSource == null || this.geometryShaderSource == otherProgram!!.geometryShaderSource) &&
                this.vertexShaderSource == otherProgram.vertexShaderSource &&
                this.fragmentShaderSource == otherProgram.fragmentShaderSource &&
                this.defines.isEmpty()) {
            true
        } else false
    }

    override fun hashCode(): Int {
        var hash = 0
        hash += geometryShaderSource?.hashCode() ?: 0
        hash += vertexShaderSource?.hashCode() ?: 0
        hash += fragmentShaderSource?.hashCode() ?: 0
        hash += if (defines != null) defines.hashCode() else 0
        return hash
    }

    private fun bindShaderAttributeChannels() {
        //		LOGGER.de.hanno.hpengine.log(Level.INFO, "Binding de.hanno.hpengine.shader input channels:");
        val channels = EnumSet.allOf(DataChannels::class.java)
        for (channel in channels) {
            glBindAttribLocation(id, channel.location, channel.binding)
            //			LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("Program(%d): Bound GL attribute location for %s with %s", id, channel.getLocation(), channel.getBinding()));
        }
    }

    fun delete() {
        glUseProgram(0)
        glDeleteProgram(id)
    }

    override fun getId(): Int {
        return id
    }

    fun addDefine(name: String, define: Any) {
        localDefines[name] = define
    }

    fun removeDefine(name: String) {
        localDefines.remove(name)
    }

    @Subscribe
    @Handler
    override fun handle(e: GlobalDefineChangedEvent) {
        reload()
    }

    companion object {
        private val LOGGER = getLogger()

        fun getDefineTextForObject(define: Map.Entry<String, Any>): String {
            if (define.value is Boolean) {
                return "const bool " + define.key + " = " + define.value.toString() + ";\n"
            } else if (define.value is Int) {
                return "const int " + define.key + " = " + define.value.toString() + ";\n"
            } else if (define.value is Float) {
                return "const float " + define.key + " = " + define.value.toString() + ";\n"
            } else {
                Logger.getGlobal().info("Local define not supported type for " + define.key + " - " + define.value)
                return ""
            }
        }

    }

}

private fun AbstractProgram.removeOldAndAddNewListeners(sources: List<CodeSource>, reloadable: Reloadable) {
    fileListeners.apply {
        FileMonitor.monitor.observers.forEach { observer ->
            fileListeners.forEach { listener ->
                observer.removeListener(listener)
            }
        }
        clear()
        addAll(sources.create(reloadable))
    }
}

fun Program.create() {
    val sources: List<CodeSource> = listOfNotNull(fragmentShaderSource, vertexShaderSource, geometryShaderSource)
    removeOldAndAddNewListeners(sources, this)
}

fun ComputeShaderProgram.create() {
    val sources: List<CodeSource> = listOfNotNull(computeShaderSource)
    removeOldAndAddNewListeners(sources, this)
}

fun List<CodeSource>.create(reloadable: Reloadable): List<OnFileChangeListener> {
    return map { codeSource ->
        FileMonitor.addOnFileChangeListener(
                codeSource.file,
                { file -> file.name.startsWith("globals") },
                { file -> reloadable.reload() }
        )
    }
}