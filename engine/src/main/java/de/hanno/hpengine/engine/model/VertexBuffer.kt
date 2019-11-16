package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import org.lwjgl.opengl.GL15
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.EnumSet
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

open class VertexBuffer(gpuContext: GpuContext<*>,
                        val channels: EnumSet<DataChannels>,
                        values: FloatArray) : PersistentMappedBuffer(gpuContext, values.size * java.lang.Float.BYTES, GL15.GL_ARRAY_BUFFER) {


    constructor(gpuContext: GpuContext<*>,
                buffer: FloatBuffer,
                channels: EnumSet<DataChannels>) : this(gpuContext, channels, FloatArray(buffer.capacity()).apply { buffer.get(this) })

    var verticesCount: Int = calculateVerticesCount(buffer, channels)
        private set
    var triangleCount: Int = verticesCount / 3
        private set

    private var vertexArrayObject: VertexArrayObject = gpuContext.calculate {
        VertexArrayObject.getForChannels(gpuContext, channels)
    }

    init {
        ensureCapacityInBytes(values.size * java.lang.Float.BYTES)
        putValues(*values)
    }

    private val uploaded = true

    val vertexData: FloatArray
        get() {
            val totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels)

            val result = FloatArray(totalElementsPerVertex * verticesCount)

            buffer.rewind()
            buffer.asFloatBuffer().get(result)
            return result
        }

    enum class Usage constructor(val value: Int) {
        DYNAMIC(GL15.GL_DYNAMIC_DRAW),
        STATIC(GL15.GL_STATIC_DRAW)
    }

    fun buffer(vertices: FloatArray): FloatBuffer {
        return buffer(vertices, channels)
    }

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

    fun totalElementsPerVertex(): Int {
        return DataChannels.totalElementsPerVertex(this.channels)

    }

    fun upload(): CompletableFuture<VertexBuffer> {
        buffer.rewind()
        val future = CompletableFuture<VertexBuffer>()
        gpuContext.execute("VertexBuffer.upload") {
            bind()
//             Don't remove this, will break things
            vertexArrayObject = VertexArrayObject.getForChannels(gpuContext, channels)
            future.complete(this@VertexBuffer)
        }
        return future
    }

    fun delete() {
        GL15.glDeleteBuffers(id)
        vertexArrayObject.delete()
    }

    override fun bind() {
        LOGGER.finest("bind called")
        super.bind()
        vertexArrayObject.bind()
    }

    override fun putValues(floatOffset: Int, vararg values: Float) {
        ensureCapacityInBytes((floatOffset + values.size) * java.lang.Float.BYTES)
        val floatBuffer = buffer.asFloatBuffer()
        floatBuffer.position(floatOffset)
        floatBuffer.put(values)
        buffer.rewind()

        val totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels)
        verticesCount = (floatOffset + values.size) / totalElementsPerVertex
        triangleCount = verticesCount / 3
    }

    fun getValues(forChannel: DataChannels): FloatArray {
        var stride = 0

        for (channel in channels) {
            if (channel == forChannel) {
                break
            } else {
                stride += channel.size
            }
        }

        val elementCountAfterPositions = totalElementsPerVertex() - (stride + forChannel.size)

        val result = FloatArray(verticesCount * forChannel.size)
        var resultIndex = 0

        val elementsPerChannel = forChannel.size
        val floatBuffer = buffer.asFloatBuffer()
        var vertexCounter = 0
        var i = stride
        while (i < floatBuffer.capacity() && vertexCounter < verticesCount) {
            for (x in 0 until forChannel.size) {

                result[resultIndex] = floatBuffer.get(i + x)
                resultIndex++
            }
            vertexCounter++
            i += stride + elementsPerChannel + elementCountAfterPositions
        }

        return result

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