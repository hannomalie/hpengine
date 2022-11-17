package de.hanno.hpengine.graphics.shader


import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.pipelines.AtomicCounterBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.GpuBuffer
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.vertexbuffer.DataChannels
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.ressources.FileMonitor
import de.hanno.hpengine.ressources.OnFileChangeListener
import de.hanno.hpengine.ressources.Reloadable
import de.hanno.hpengine.ressources.WrappedCodeSource
import de.hanno.hpengine.transform.x
import de.hanno.hpengine.transform.y
import de.hanno.hpengine.transform.z
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
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
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer
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

    val gpuContext: GpuContext = programManager.gpuContext

    override var shaders: List<de.hanno.hpengine.graphics.shader.api.Shader> = listOfNotNull(vertexShader, fragmentShader, geometryShader, tesselationControlShader, tesselationEvaluationShader)

    var longBuffer: LongBuffer? = null

    override val name: String = StringJoiner(", ").apply {
        geometryShader?.let { add(it.name) }
        add(vertexShader.name)
        fragmentShader?.let { add(it.name) }
    }.toString()

    override fun load() = gpuContext.onGpu {
        shaders.forEach { attach(it) }

        bindShaderAttributeChannels()
        linkProgram()
        validateProgram()

        registerUniforms()
        gpuContext.exceptionOnError()

        createFileListeners()
    }

    fun registerUniforms() {
        uniformBindings.clear()
        uniforms.registeredUniforms.forEach {
            uniformBindings[it.name] = when(it) {
                is SSBO -> UniformBinding(it.name, it.bindingIndex)
                is BooleanType -> UniformBinding(it.name, getUniformLocation(it.name))
                is FloatType -> UniformBinding(it.name, getUniformLocation(it.name))
                is IntType -> UniformBinding(it.name, getUniformLocation(it.name))
                is Mat4 -> UniformBinding(it.name, getUniformLocation(it.name))
                is Vec3 -> UniformBinding(it.name, getUniformLocation(it.name))
            }
        }
    }

    private fun attach(shader: de.hanno.hpengine.graphics.shader.api.Shader) {
        glAttachShader(id, shader.id)
        gpuContext.getExceptionOnError("Couldn't attach, maybe is already attached: shader.name")

    }

    private fun detach(shader: de.hanno.hpengine.graphics.shader.api.Shader) {
        glDetachShader(id, shader.id)
    }

    override fun unload() {
        glDeleteProgram(id)
        shaders.forEach { it.unload() }
    }

    override fun reload() = try {
        gpuContext.onGpu {
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


    // TODO: Extract all those things to an abstractopenglprogram or to programmanager

    override fun setUniform(name: String, value: Int) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    override fun setUniform(name: String, value: Boolean) {
        val valueAsInt = if (value) 1 else 0
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(valueAsInt)
    }

    override fun setUniform(name: String, value: Float) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    override fun setUniform(name: String, value: Long) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value)
    }

    override fun setUniform(name: String, longs: LongArray) {
        if (longBuffer == null) {
            val newLongBuffer = BufferUtils.createLongBuffer(longs.size)
            longBuffer = newLongBuffer
            newLongBuffer.rewind()
            newLongBuffer.put(longs)
        }
        setUniform(name, longBuffer)
    }

    override fun setUniform(name: String, buffer: LongBuffer?) {
        buffer!!.rewind()
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(buffer)
    }

    override fun setUniform(name: String, value: Double) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(value.toFloat())
    }

    override fun setUniformAsMatrix4(name: String, matrixBuffer: FloatBuffer) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setAsMatrix4(matrixBuffer)
    }

    override fun setUniformAsMatrix4(name: String, matrixBuffer: ByteBuffer) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setAsMatrix4(matrixBuffer)
    }

    override fun setUniform(name: String, x: Float, y: Float, z: Float) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(x, y, z)
    }

    override fun setUniform(name: String, vec: Vector3f) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(vec.x, vec.y, vec.z)
    }
    override fun setUniform(name: String, vec: Vector3fc) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(vec.x, vec.y, vec.z)
    }
    override fun setUniform(name: String, vec: Vector2f) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.set(vec.x, vec.y)
    }

    override fun setUniformVector3ArrayAsFloatBuffer(name: String, values: FloatBuffer) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setVec3ArrayAsFloatBuffer(values)
    }

    override fun setUniformFloatArrayAsFloatBuffer(name: String, values: FloatBuffer) {
        createBindingIfMissing(name)
        uniformBindings[name]!!.setFloatArrayAsFloatBuffer(values)
    }

    private fun createBindingIfMissing(name: String) {
        if (!uniformBindings.containsKey(name)) {
            uniformBindings[name] = UniformBinding(name, getUniformLocation(name))
        }
    }
    private fun createShaderStorageBindingIfMissing(name: String) {
        if (!uniformBindings.containsKey(name)) {
            uniformBindings[name] = UniformBinding(name, getShaderStorageBlockIndex(name))
        }
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
        gpuContext.exceptionOnError()
    }

    fun delete() {
        glUseProgram(0)
        glDeleteProgram(id)
    }

    override fun use() {
        glUseProgram(id)
    }

    override fun getUniformLocation(name: String): Int {
        return GL20.glGetUniformLocation(id, name)
    }

    override fun bindShaderStorageBuffer(index: Int, block: GpuBuffer) {
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, block.id)
    }

    override fun bindAtomicCounterBufferBuffer(index: Int, block: AtomicCounterBuffer) {
        GL30.glBindBufferBase(GL42.GL_ATOMIC_COUNTER_BUFFER, index, block.id)
    }

    override fun getShaderStorageBlockIndex(name: String): Int {
        return GL43.glGetProgramResourceIndex(id, GL43.GL_SHADER_STORAGE_BLOCK, name)
    }

    override fun getShaderStorageBlockBinding(name: String, bindingIndex: Int) {
        GL43.glShaderStorageBlockBinding(id, getShaderStorageBlockIndex(name), bindingIndex)
    }

    override fun UniformDelegate<*>.bind() = when (this) {
        is Mat4 -> GL20.glUniformMatrix4fv(uniformBindings[name]!!.location, false, _value)
        is Vec3 -> GL20.glUniform3f(uniformBindings[name]!!.location, _value.x, _value.y, _value.z)
        is SSBO -> GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, uniformBindings[name]!!.location, _value.id)
        is IntType -> GL20.glUniform1i(uniformBindings[name]!!.location, _value)
        is BooleanType -> GL20.glUniform1i(uniformBindings[name]!!.location, if(_value) 1 else 0)
        is FloatType -> GL20.glUniform1f(uniformBindings[name]!!.location, _value)
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

inline fun <T: Uniforms> IProgram<T>.useAndBind(block: (T) -> Unit) {
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

fun FileBasedCodeSource.registerFileChangeListener(reloadable: Reloadable) = FileMonitor.addOnFileChangeListener(
    file,
    { file ->
        file.name.startsWith("globals")
    },
    {
        reloadable.reload()
    }
)