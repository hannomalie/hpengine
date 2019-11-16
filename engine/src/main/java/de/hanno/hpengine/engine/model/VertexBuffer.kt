package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import org.lwjgl.opengl.ARBIndirectParameters
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL31
import org.lwjgl.opengl.GL42

import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.EnumSet
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

import org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect
open class VertexBuffer(gpuContext: GpuContext<*>,
                        val channels: EnumSet<DataChannels>,
                        values: FloatArray): PersistentMappedBuffer(gpuContext, values.size * java.lang.Float.BYTES, GL15.GL_ARRAY_BUFFER) {


    constructor(gpuContext: GpuContext<*>,
                buffer: FloatBuffer,
                channels: EnumSet<DataChannels>): this(gpuContext, channels, FloatArray(buffer.capacity()).apply { buffer.get(this) })

    private var verticesCount: Int = calculateVerticesCount(buffer, channels)
    private var triangleCount: Int = verticesCount / 3
    init {
        gpuContext.execute("VertexBuffer.setInternals") {
            bind()
            setVertexArrayObject(VertexArrayObject.getForChannels(gpuContext, channels))
        }
        ensureCapacityInBytes(values.size * java.lang.Float.BYTES)
        putValues(*values)
    }

    private val uploaded = true

    private var vertexArrayObject: VertexArrayObject? = null

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

    fun getVerticesCount(): Int {
        triangleCount = verticesCount / 3
        return verticesCount
    }

    private fun calculateVerticesCount(channels: EnumSet<DataChannels>): Int {
        val totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels)
        validate(totalElementsPerVertex)

        val verticesCount = buffer.capacity() / java.lang.Float.BYTES / totalElementsPerVertex
        triangleCount = verticesCount / 3
        return verticesCount
    }

    fun totalElementsPerVertex(): Int {
        return DataChannels.totalElementsPerVertex(this.channels)

    }

    // TODO: Mach irgendwas....
    private fun validate(totalElementsPerVertex: Int) {
        val modulo = buffer.capacity() % totalElementsPerVertex
        if (modulo != 0) {
            throw RuntimeException(String.format("Can't buffer those vertices!\n" +
                    "vertices count: %d,\n" +
                    "Attribute values per vertex: %d\n" +
                    "=> Modulo is %d", buffer.capacity(), totalElementsPerVertex, modulo))
        }
    }

    fun upload(): CompletableFuture<VertexBuffer> {
        buffer.rewind()
        val future = CompletableFuture<VertexBuffer>()
        gpuContext.execute("VertexBuffer.upload") {
            bind()
            setVertexArrayObject(VertexArrayObject.getForChannels(gpuContext, channels))
            future.complete(this@VertexBuffer)
        }
        return future
    }

    fun delete() {
        GL15.glDeleteBuffers(id)
        vertexArrayObject!!.delete()
    }

    @JvmOverloads
    fun draw(indexBuffer: IndexBuffer? = null): Int {
        if (!uploaded) {
            return -1
        }
        bind()
        return drawActually(indexBuffer)
    }

    /**
     *
     * @return triangleCount that twas drawn
     */
    private fun drawActually(indexBuffer: IndexBuffer?): Int {
        LOGGER.finest("drawActually called")
        if (indexBuffer != null) {
            indexBuffer.bind()
            val indices = indexBuffer.buffer.asIntBuffer()
            GL11.glDrawElements(GL11.GL_TRIANGLES, indices)
            return indices.capacity() / 3
        } else {
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verticesCount)
            return verticesCount
        }
    }

    fun drawInstanced(indexBuffer: IndexBuffer?, indexCount: Int, instanceCount: Int, indexOffset: Int, baseVertexIndex: Int): Int {
        if (!uploaded) {
            return 0
        }
        bind()
        indexBuffer?.let { drawInstancedBaseVertex(it, indexCount, instanceCount, indexOffset, baseVertexIndex) }
                ?: GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, verticesCount, instanceCount)

        return verticesCount
    }


    /**
     *
     *
     * @param indexCount
     * @param instanceCount
     * @param indexOffset
     * @param baseVertexIndex the integer index, not the byte offset
     * @return
     */
    fun drawInstancedBaseVertex(indexBuffer: IndexBuffer?, indexCount: Int, instanceCount: Int, indexOffset: Int, baseVertexIndex: Int): Int {
        if (!uploaded) {
            return 0
        }
        bind()
        if (indexBuffer != null) {
            indexBuffer.bind()
            GL42.glDrawElementsInstancedBaseVertexBaseInstance(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, (4 * indexOffset).toLong(), instanceCount, baseVertexIndex, 0)

        } else {
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, verticesCount, instanceCount)
        }

        return indexCount
    }

    fun drawInstancedBaseVertex(indexBuffer: IndexBuffer, command: DrawElementsIndirectCommand): Int {
        return drawInstancedBaseVertex(indexBuffer, command.count, command.primCount, command.firstIndex, command.baseVertex)
    }

    fun drawLinesInstancedBaseVertex(indexBuffer: IndexBuffer?, indexCount: Int, instanceCount: Int, indexOffset: Int, baseVertexIndex: Int): Int {
        if (!uploaded) {
            return 0
        }
        bind()
        if (indexBuffer != null) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
            GL11.glLineWidth(1f)
            indexBuffer.bind()
            GL42.glDrawElementsInstancedBaseVertexBaseInstance(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, (4 * indexOffset).toLong(), instanceCount, baseVertexIndex, 0)
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)

        } else {
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, verticesCount, instanceCount)
        }

        return indexCount / 3
    }

    fun drawLinesInstancedBaseVertex(indexBuffer: IndexBuffer, command: DrawElementsIndirectCommand): Int {
        return drawLinesInstancedBaseVertex(indexBuffer, command.count, command.primCount, command.firstIndex, command.baseVertex)
    }

    override fun bind() {
        LOGGER.finest("bind called")
        super.bind()
        if (vertexArrayObject != null) {
            vertexArrayObject!!.bind()
        }
    }

    override fun putValues(floatOffset: Int, vararg values: Float) {
        //        bind();
        ensureCapacityInBytes((floatOffset + values.size) * java.lang.Float.BYTES)
        val floatBuffer = buffer.asFloatBuffer()
        floatBuffer.position(floatOffset)
        floatBuffer.put(values)
        buffer.rewind()

        val totalElementsPerVertex = DataChannels.totalElementsPerVertex(channels)
        verticesCount = (floatOffset + values.size) / totalElementsPerVertex
        triangleCount = verticesCount / 3
    }

    fun drawDebug(): Int {
        return drawDebug()
    }

    fun drawDebug(indexBuffer: IndexBuffer): Int {
        if (!uploaded) {
            return 0
        }
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        GL11.glLineWidth(1f)
        bind()
        drawActually(indexBuffer)
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
        return verticesCount
    }

    fun drawDebug(indexBuffer: IndexBuffer, lineWidth: Float): Int {
        if (!uploaded) {
            return 0
        }
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        GL11.glLineWidth(lineWidth)
        bind()
        drawActually(indexBuffer)
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
        return verticesCount
    }

    @JvmOverloads
    fun drawDebugLines(lineWidth: Float = 2f): Int {
        if (!uploaded) {
            return 0
        }
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        GL11.glLineWidth(lineWidth)
        bind()
        GL11.glDrawArrays(GL11.GL_LINES, 0, verticesCount)
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
        return verticesCount
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

    private fun setVertexArrayObject(vertexArrayObject: VertexArrayObject) {
        this.vertexArrayObject = vertexArrayObject
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

        fun multiDrawElementsIndirectCount(vertexBuffer: VertexBuffer,
                                           indexBuffer: IndexBuffer,
                                           commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>,
                                           drawCountBuffer: AtomicCounterBuffer,
                                           maxDrawCount: Int) {
            drawCountBuffer.bindAsParameterBuffer()
            vertexBuffer.bind()
            indexBuffer.bind()
            commandBuffer.bind()
            ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, 0, maxDrawCount, 0)
            drawCountBuffer.unbind()
            indexBuffer.unbind()
        }

        fun multiDrawElementsIndirectCount(vertexIndexBuffer: VertexIndexBuffer,
                                           commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>,
                                           drawCountBuffer: AtomicCounterBuffer,
                                           maxDrawCount: Int) {
            multiDrawElementsIndirectCount(vertexIndexBuffer.vertexBuffer,
                    vertexIndexBuffer.indexBuffer, commandBuffer, drawCountBuffer, maxDrawCount)
        }

        fun multiDrawElementsIndirect(vertexBuffer: VertexBuffer, indexBuffer: IndexBuffer, commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>, primitiveCount: Int) {
            vertexBuffer.bind()
            indexBuffer.bind()
            commandBuffer.bind()
            glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, primitiveCount, 0)
            indexBuffer.unbind()
        }

        fun drawLinesInstancedIndirectBaseVertex(vertexIndexBuffer: VertexIndexBuffer, commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>, primitiveCount: Int) {
            drawLinesInstancedIndirectBaseVertex(vertexIndexBuffer.vertexBuffer, vertexIndexBuffer.indexBuffer, commandBuffer, primitiveCount)
        }

        fun drawLinesInstancedIndirectBaseVertex(vertexBuffer: VertexBuffer, indexBuffer: IndexBuffer, commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>, primitiveCount: Int) {
            vertexBuffer.bind()
            indexBuffer.bind()
            commandBuffer.bind()
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
            GL11.glLineWidth(1f)
            glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, primitiveCount, 0)
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
        }
    }

}

