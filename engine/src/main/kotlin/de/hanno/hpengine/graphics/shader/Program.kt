package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.vertexbuffer.DataChannels
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.ressources.FileMonitor
import de.hanno.hpengine.ressources.OnFileChangeListener
import de.hanno.hpengine.ressources.Reloadable
import de.hanno.hpengine.ressources.WrappedCodeSource
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
import java.util.StringJoiner

class Program<T : Uniforms> constructor(
    programManager: OpenGlProgramManager,
    val vertexShader: VertexShader,
    val tesselationControlShader: TesselationControlShader? = null,
    val tesselationEvaluationShader: TesselationEvaluationShader? = null,
    val geometryShader: GeometryShader? = null,
    val fragmentShader: FragmentShader? = null,
    defines: Defines = Defines(),
    uniforms: T
) : AbstractProgram<T>(programManager.gpuContext.createProgramId(), defines, uniforms) {

    val gpuContext: GpuContext<OpenGl> = programManager.gpuContext

    override var shaders: List<Shader> = listOfNotNull(vertexShader, fragmentShader, geometryShader, tesselationControlShader, tesselationEvaluationShader)

    override val name: String = StringJoiner(", ").apply {
        geometryShader?.let { add(it.name) }
        add(vertexShader.name)
        fragmentShader?.let { add(it.name) }
    }.toString()

    override fun load() = gpuContext.invoke {
        shaders.forEach { attach(it) }

        bindShaderAttributeChannels()
        linkProgram()
        validateProgram()

        registerUniforms()
        gpuContext.backend.gpuContext.exceptionOnError()

        createFileListeners()
    }

    private fun attach(shader: Shader) {
        glAttachShader(id, shader.id)
        gpuContext.getExceptionOnError("Couldn't attach, maybe is already attached: shader.name")

    }

    private fun detach(shader: Shader) {
        glDetachShader(id, shader.id)
    }

    override fun unload() {
        glDeleteProgram(id)
        shaders.forEach { it.unload() }
    }

    override fun reload() = try {
        gpuContext.invoke {
            shaders.forEach {
                detach(it)
                gpuContext.getExceptionOnError("Couldn't detach, maybe is already detached")
                it.reload()
            }

            load()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Program<*>) return false

        return geometryShader == other.geometryShader &&
                tesselationControlShader == other.tesselationControlShader &&
                tesselationEvaluationShader == tesselationEvaluationShader &&
                vertexShader == other.vertexShader &&
                fragmentShader == other.fragmentShader &&
                defines == other.defines
    }

    override fun hashCode(): Int {
        var hash = 0
        hash += geometryShader.hashCode()
        hash += vertexShader.hashCode()
        hash += fragmentShader.hashCode()
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

    init {
        load()
    }

    companion object {
        val Map.Entry<String, Any>.defineText: String
            get() = when (value) {
                is Boolean -> "const bool $key = $value;\n"
                is Int -> "const int $key = $value;\n"
                is Float -> "const float $key = $value;\n"
                else -> throw java.lang.IllegalStateException("Local define not supported type for $key - $value")
            }

    }

}

fun <T : Uniforms> AbstractProgram<T>.validateProgram() {
    glValidateProgram(id)
    val validationResult = glGetProgrami(id, GL_VALIDATE_STATUS)
    if (GL11.GL_FALSE == validationResult) {
        System.err.println(glGetProgramInfoLog(id))
        throw IllegalStateException("Program invalid: $name")
    }
}

fun <T : Uniforms> AbstractProgram<T>.linkProgram() {
    glLinkProgram(id)
    val linkResult = glGetProgrami(id, GL_LINK_STATUS)
    if (GL11.GL_FALSE == linkResult) {
        System.err.println(glGetProgramInfoLog(id))
        throw IllegalStateException("Program not linked: $name")
    }
}

inline fun <T: Uniforms> Program<T>.useAndBind(block: (T) -> Unit) {
    use()
    block(uniforms)
    bind()
}

fun AbstractProgram<*>.replaceOldListeners(sources: List<FileBasedCodeSource>, reloadable: Reloadable) {
    removeOldListeners()
    fileListeners.addAll(sources.registerFileChangeListeners(reloadable))
}

fun AbstractProgram<*>.removeOldListeners() {
    FileMonitor.monitor.observers.forEach { observer ->
        fileListeners.forEach { listener ->
            observer.removeListener(listener)
        }
    }
    fileListeners.clear()
}

fun Program<*>.createFileListeners() {
    val sources = listOfNotNull(
        fragmentShader?.source,
        vertexShader.source,
        geometryShader?.source,
        tesselationControlShader?.source,
        tesselationEvaluationShader?.source,
    )
    val fileBasedSources = sources.filterIsInstance<FileBasedCodeSource>() + sources.filterIsInstance<WrappedCodeSource>().map { it.underlying }

    replaceOldListeners(fileBasedSources, this)
}

fun ComputeProgram.createFileListeners() {
    val sources: List<FileBasedCodeSource> = listOf(computeShader.source).filterIsInstance<FileBasedCodeSource>()
    replaceOldListeners(sources, this)
}

fun List<FileBasedCodeSource>.registerFileChangeListeners(reloadable: Reloadable): List<OnFileChangeListener> {
    return map { it.registerFileChangeListener(reloadable) }
}

fun FileBasedCodeSource.registerFileChangeListener(reloadable: Reloadable) = de.hanno.hpengine.ressources.FileMonitor.addOnFileChangeListener(
    file,
    { file ->
        file.name.startsWith("globals")
    },
    {
        reloadable.reload()
    }
)