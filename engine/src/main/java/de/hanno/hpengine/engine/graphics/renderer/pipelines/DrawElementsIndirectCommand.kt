package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.struct.Array
import de.hanno.struct.SlidingWindow
import de.hanno.struct.Struct
import de.hanno.struct.copyTo
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL15.glUnmapBuffer
import org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT
import org.lwjgl.opengl.GL30.glMapBufferRange
import org.lwjgl.opengl.GL40
import org.lwjgl.opengl.GL44
import org.lwjgl.opengl.GL44.GL_MAP_COHERENT_BIT
import org.lwjgl.opengl.GL44.GL_MAP_PERSISTENT_BIT
import java.nio.ByteBuffer

class DrawElementsIndirectCommand : Struct() {
    var count by 0
    var primCount by 0
    var firstIndex by 0
    var baseVertex by 0
    var baseInstance by 0
}

private val bufferFlags = GL_MAP_WRITE_BIT or GL_MAP_PERSISTENT_BIT or GL_MAP_COHERENT_BIT

class PersistentMappedStructBuffer<T: Struct>(initialSize: Int,
                                              val factory: () -> T,
                                              val gpuContext: GpuContext<*>,
                                              val target: Int): Array<T> {
    val slidingWindow = SlidingWindow(factory()).apply {
        underlying.provideBuffer = { buffer }
    }

    override var buffer = create(sizeInBytes.toLong())
        private set(value) {
            field = value
            size = value.capacity() / slidingWindow.sizeInBytes
        }

    override var size = initialSize
        private set(value) {
            field = value
            indices = IntRange(0, size)
        }

    override var indices = IntRange(0, size)
        private set

    override val sizeInBytes: Int
        get() = size * slidingWindow.sizeInBytes


    var id: Int = -1
       private set

    fun mapBuffer(capacityInBytes: Long): ByteBuffer {
        val nonZeroCapacityInBytes = if(capacityInBytes <= 0) 1 else capacityInBytes
        return gpuContext.calculate {
            bind()
            // TODO: Use orphaning
            // TODO: This causes segfaults in Unsafe class, wtf...
            // ByteBuffer xxxx = BufferUtils.createByteBuffer((int) capacityInBytes);
            val byteBuffer = glMapBufferRange(target,
                    0, nonZeroCapacityInBytes,
                    bufferFlags, null) ?: throw IllegalStateException("Cannot create mapped buffer")

//            dont remove safe call, since mapBuffer is caled in buffer initializer itself :)
            buffer?.let {
                synchronized(it) {
                    it.copyTo(byteBuffer)
                }
            }
            byteBuffer.rewind()
            byteBuffer
        }
    }
    fun bind() {
        gpuContext.execute("PersistentMappedStructBuffer.bind") {
            if (id <= 0) {
                id = glGenBuffers()
            }
            glBindBuffer(target, id)
        }
    }

    fun unbind() {
        gpuContext.execute("PersistentMappedStructBuffer.unbind") { glBindBuffer(target, 0) }
    }

    @Synchronized
    fun resize(requestedCapacity: Int) {
        dispose()
        buffer = create(requestedCapacity.toLong() * slidingWindow.sizeInBytes)
    }

    private fun create(capacityInBytes: Long): ByteBuffer {
        val nonZeroCapacityInBytes = if(capacityInBytes <= 0) 1 else capacityInBytes
        return gpuContext.calculate {
            bind()
            GL44.glBufferStorage(target, nonZeroCapacityInBytes, bufferFlags)

            val buffer = mapBuffer(nonZeroCapacityInBytes)
            unbind()
            buffer
        }
    }

    private fun dispose() {
        gpuContext.execute("PersistentMappedStructBuffer.dispose") {
            bind()
            if (GL15.glGetBufferParameteri(target, GL15.GL_BUFFER_MAPPED) == GL11.GL_TRUE) {
                glUnmapBuffer(target)
            }
            unbind()
            if (id > 0) {
                glDeleteBuffers(id)
                id = -1
            }
        }
    }
    fun shrinkToBytes(sizeInBytes: Int, copyContent: Boolean = true) = shrink(sizeInBytes, copyContent)

    fun shrink(sizeInBytes: Int, copyContent: Boolean = true): PersistentMappedStructBuffer<T> {
        if(buffer.capacity() > sizeInBytes) {
            resize(sizeInBytes)
        }
        return this
    }

    fun enlarge(size: Int, copyContent: Boolean = true) = enlargeToBytes(size * slidingWindow.sizeInBytes, copyContent)

    fun enlargeToBytes(sizeInBytes: Int, copyContent: Boolean = true): PersistentMappedStructBuffer<T> {
        if(buffer.capacity() < sizeInBytes) {
            resize(sizeInBytes)
        }
        return this
    }

    override operator fun get(index: Int): T {
        val currentSlidingWindow = slidingWindow
        currentSlidingWindow.localByteOffset = (index * currentSlidingWindow.sizeInBytes).toLong()
        return currentSlidingWindow.underlying
    }
}

fun CommandBuffer(gpuContext: GpuContext<*>, size: Int = 1000): PersistentMappedStructBuffer<DrawElementsIndirectCommand> {
    return PersistentMappedStructBuffer(size, { DrawElementsIndirectCommand() }, gpuContext, GL40.GL_DRAW_INDIRECT_BUFFER)
}

class IntStruct: Struct() {
    var value by 0
}

fun IndexBuffer(gpuContext: GpuContext<*>, size: Int = 1000): PersistentMappedStructBuffer<IntStruct> {
    return PersistentMappedStructBuffer(size, { IntStruct() }, gpuContext, GL40.GL_ELEMENT_ARRAY_BUFFER)
}

