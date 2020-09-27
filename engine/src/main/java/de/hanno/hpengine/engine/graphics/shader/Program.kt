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
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
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

class Program(
        private val programManager: OpenGlProgramManager,
        val vertexShaderSource: CodeSource,
        val geometryShaderSource: CodeSource?,
        val fragmentShaderSource: CodeSource?,
        defines: Defines
) : AbstractProgram(programManager.gpuContext.createProgramId()) {
    val gpuContext: GpuContext<OpenGl>

    private val localDefines = HashMap<String, Any>()

    override var shaders: List<Shader> = emptyList()
    private lateinit var vertexShader: VertexShader
    private var geometryShader: GeometryShader? = null
    private var fragmentShader: FragmentShader? = null

    val defineString: String
        get() {
            val builder = StringBuilder()

            for (shaderDefine in localDefines.entries) {
                builder.append(shaderDefine.defineText)
                builder.append("\n")
            }
            return builder.toString()
        }

    init {
        this.gpuContext = programManager.gpuContext
        this.defines = defines

        load()
    }

    override fun load() {
        gpuContext.invoke {
            clearUniforms()

            vertexShader = VertexShader.load(programManager, vertexShaderSource, defines)
            fragmentShader = fragmentShaderSource?.let { FragmentShader.load(programManager, it, defines) }
            geometryShader = geometryShaderSource?.let { GeometryShader.load(programManager, it, defines) }

            attachShader(vertexShader)
            fragmentShader?.let { attachShader(it) }
            geometryShader?.let { attachShader(it) }

            bindShaderAttributeChannels()
            linkProgram()
            validateProgram()

            gpuContext.backend.gpuContext.exceptionOnError()

            this.create()

            shaders = listOfNotNull(vertexShader, fragmentShader, geometryShader)
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
        glAttachShader(id, shader.id)
        gpuContext.backend.gpuContext.exceptionOnError(shader.name)

    }

    private fun detachShader(shader: Shader) {
        glDetachShader(id, shader.id)
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
        gpuContext.invoke {
            detachShader(vertexShader)
            fragmentShader?.let { fragmentShader ->
                detachShader(fragmentShader)
                fragmentShader.reload()
            }
            geometryShader?.let { geometryShader ->
                detachShader(geometryShader)
                geometryShader.reload()
            }
            vertexShader.reload()

            load()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    override val name: String = StringJoiner(", ")
            .add(fragmentShaderSource?.name ?: "")
            .add(vertexShaderSource.name).toString()

    override fun equals(other: Any?): Boolean {
        if (other !is Program) {
            return false
        }

        val otherProgram = other as Program?

        return (this.geometryShaderSource == null && otherProgram!!.geometryShaderSource == null || this.geometryShaderSource == otherProgram!!.geometryShaderSource) &&
                this.vertexShaderSource == otherProgram.vertexShaderSource &&
                this.fragmentShaderSource == otherProgram.fragmentShaderSource &&
                this.defines.isEmpty()
    }

    override fun hashCode(): Int {
        var hash = 0
        hash += geometryShaderSource?.hashCode() ?: 0
        hash += vertexShaderSource.hashCode()
        hash += fragmentShaderSource?.hashCode() ?: 0
        hash += defines.hashCode()
        return hash
    }

    private fun bindShaderAttributeChannels() {
        val channels = EnumSet.allOf(DataChannels::class.java)
        for (channel in channels) {
            glBindAttribLocation(id, channel.location, channel.binding)
        }
        gpuContext.backend.gpuContext.exceptionOnError()
    }

    fun delete() {
        glUseProgram(0)
        glDeleteProgram(id)
    }

    fun addDefine(name: String, define: Any) {
        localDefines[name] = define
    }

    fun removeDefine(name: String) {
        localDefines.remove(name)
    }

    @Subscribe
    @Handler
    fun handle(e: GlobalDefineChangedEvent) {
        reload()
    }

    companion object {
        private val LOGGER = getLogger()

        val Map.Entry<String, Any>.defineText: String
            get() = when (value) {
                is Boolean -> "const bool $key = $value;\n"
                is Int -> "const int $key = $value;\n"
                is Float -> "const float $key = $value;\n"
                else -> throw java.lang.IllegalStateException("Local define not supported type for $key - $value")
            }

    }

}

private fun AbstractProgram.replaceOldListeners(sources: List<FileBasedCodeSource>, reloadable: Reloadable) {
    removeOldListeners()
    fileListeners.addAll(sources.toFileChangeListeners(reloadable))
}

private fun AbstractProgram.removeOldListeners() {
    FileMonitor.monitor.observers.forEach { observer ->
        fileListeners.forEach { listener ->
            observer.removeListener(listener)
        }
    }
    fileListeners.clear()
}

fun Program.create() {
    val sources: List<FileBasedCodeSource> = listOfNotNull(fragmentShaderSource, vertexShaderSource, geometryShaderSource)
            .filterIsInstance<FileBasedCodeSource>()
    replaceOldListeners(sources, this)
}

fun ComputeProgram.create() {
    val sources: List<FileBasedCodeSource> = listOfNotNull(computeShaderSource)
    replaceOldListeners(sources, this)
}

fun List<FileBasedCodeSource>.toFileChangeListeners(reloadable: Reloadable): List<OnFileChangeListener> {
    return map { codeSource ->
        FileMonitor.addOnFileChangeListener(
                codeSource.file,
                { file -> file.name.startsWith("globals") },
                { reloadable.reload() }
        )
    }
}