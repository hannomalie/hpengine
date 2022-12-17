package de.hanno.hpengine.graphics.vertexbuffer


import de.hanno.hpengine.graphics.DataChannels
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.graphics.renderer.constants.BufferTarget
import de.hanno.hpengine.graphics.renderer.pipelines.GpuBuffer
import org.joml.Vector2f
import org.lwjgl.opengl.GL15
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

context(GpuContext)
class VertexBuffer(
    _channels: EnumSet<DataChannels>,
    values: FloatArray
) : GpuBuffer, IVertexBuffer {

    private val gpuBuffer = PersistentMappedBuffer(
        BufferTarget.Array,
        values.size * java.lang.Float.BYTES
    )
    override val buffer: ByteBuffer get() = gpuBuffer.buffer
    override val target: BufferTarget = BufferTarget.Array
    override val id: Int get() = gpuBuffer.id

    override fun ensureCapacityInBytes(requestedCapacity: Int) {
        gpuBuffer.ensureCapacityInBytes(requestedCapacity)
    }

    val channels = _channels
    override val verticesCount: Int = calculateVerticesCount(buffer, channels)
    val triangleCount: Int = verticesCount / 3

    private var vertexArrayObject: VertexArrayObject = onGpu {
        VertexArrayObject.getForChannels(channels)
    }

    init {
        ensureCapacityInBytes(values.size * java.lang.Float.BYTES)
        buffer.asFloatBuffer().put(values)
    }

    enum class Usage constructor(val value: Int) {
        DYNAMIC(GL15.GL_DYNAMIC_DRAW),
        STATIC(GL15.GL_STATIC_DRAW)
    }

    fun buffer(vertices: FloatArray): FloatBuffer = buffer(vertices, channels)

    private fun buffer(vertices: FloatArray, channels: EnumSet<DataChannels>): FloatBuffer {

        val totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels)
        val verticesCount = calculateVerticesCount(vertices, channels)

        for (i in 0 until verticesCount) {
            var currentOffset = 0
            for (channel in channels) {
                for (a in 0 until channel.size) {
                    buffer.putFloat(vertices[i * totalElementsPerVertex + currentOffset + a])
                }
                currentOffset += channel.size
            }
        }

        buffer.rewind()
        return buffer.asFloatBuffer()
    }

    override fun upload(): CompletableFuture<IVertexBuffer> {
        buffer.rewind()
        val future = CompletableFuture<IVertexBuffer>()
        onGpu {
            bind()
//             Don't remove this, will break things
            vertexArrayObject = VertexArrayObject.getForChannels(channels)
            future.complete(this@VertexBuffer)
        }
        return future
    }

    override fun delete() {
        GL15.glDeleteBuffers(id)
        vertexArrayObject.delete()
    }

    override fun bind() {
        LOGGER.finest("bind called")
        gpuBuffer.bind()
        vertexArrayObject.bind()
    }

    override fun unbind() {
        gpuBuffer.unbind()
    }

    companion object {

        internal val LOGGER = Logger.getLogger(VertexBuffer::class.java.name)

        fun calculateVerticesCount(vertices: FloatArray, channels: EnumSet<DataChannels>): Int {
            val totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels)

            return vertices.size / totalElementsPerVertex
        }

        fun calculateVerticesCount(floatBuffer: ByteBuffer?, channels: EnumSet<DataChannels>): Int {
            if (floatBuffer == null) {
                return 0
            }
            floatBuffer.rewind()
            val floatArray = FloatArray(floatBuffer.asFloatBuffer().capacity())
            floatBuffer.asFloatBuffer().get(floatArray)
            return calculateVerticesCount(floatArray, channels)
        }
    }
}

fun GpuContext.createSixDebugBuffers() = onGpu {
    object : ArrayList<IVertexBuffer>() {
        init {
            val height = -2f / 3f
            val width = 2f
            val widthDiv = width / 6f
            for (i in 0..5) {
                val quadVertexBuffer = QuadVertexBuffer(
                    QuadVertexBuffer.getPositionsAndTexCoords(
                        Vector2f(-1f + i * widthDiv, -1f),
                        Vector2f(-1 + (i + 1) * widthDiv, height)
                    )
                )
                add(quadVertexBuffer)
                quadVertexBuffer.upload()
            }
        }
    }
}
