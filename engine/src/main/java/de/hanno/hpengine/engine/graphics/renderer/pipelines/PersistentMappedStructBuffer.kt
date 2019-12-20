package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.flags
import de.hanno.struct.Array
import de.hanno.struct.SlidingWindow
import de.hanno.struct.Struct
import de.hanno.struct.StructArray
import de.hanno.struct.copyTo
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL40
import org.lwjgl.opengl.GL43
import org.lwjgl.opengl.GL44
import java.nio.ByteBuffer
import kotlin.math.max

class PersistentMappedStructBuffer<T: Struct>(initialSize: Int,
                                                              val factory: () -> T,
                                                              val gpuContext: GpuContext<*>,
                                                              val target: Int = GL43.GL_SHADER_STORAGE_BUFFER): Array<T> {
    val slidingWindow = createSlidingWindow()

    fun createSlidingWindow(): SlidingWindow<T> {
        return SlidingWindow(factory()).apply {
            underlying.provideBuffer = { buffer }
        }
    }

    override lateinit var buffer: ByteBuffer

    override val size
        get() = buffer.capacity() / slidingWindow.sizeInBytes

    var id: Int = -1
        private set

    init {
        gpuContext.calculate {
            val (id, newBuffer) = createBuffer(max(initialSize * slidingWindow.sizeInBytes, 1))
            this.buffer = newBuffer
            this.id = id
        }
    }

    override val indices
        get() = IntRange(0, size)

    override val sizeInBytes: Int
        get() = size * slidingWindow.sizeInBytes

    fun mapBuffer(capacityInBytes: Long): ByteBuffer {
//            TODO: This causes segfaults in Unsafe class, wtf...
        val xxxx = BufferUtils.createByteBuffer(capacityInBytes.toInt());
        val byteBuffer = GL30.glMapBufferRange(target,
                0, capacityInBytes, flags,
//                null)!!
                xxxx)!!
        copyOldBufferTo(byteBuffer)
        return byteBuffer
    }

    private fun copyOldBufferTo(byteBuffer: ByteBuffer) {
        if (::buffer.isInitialized) {
            val array = ByteArray(buffer.capacity())
            buffer.rewind()
            buffer.get(array)
//            byteBuffer.put(buffer)
            byteBuffer.put(array)
            byteBuffer.rewind()
        }
    }

    @Synchronized
    fun ensureCapacityInBytes(requestedCapacity: Int) {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= 0) {
            capacityInBytes = 10
        }

        if (::buffer.isInitialized) {
            val needsResize = buffer.capacity() <= capacityInBytes
            if (needsResize) {
                gpuContext.calculate {
                    val (newId, newBuffer) = createBuffer(capacityInBytes)
                    copyOldBufferTo(newBuffer)
                    val oldId = this.id
                    GL15.glDeleteBuffers(oldId)
                    this.buffer = newBuffer
                    this.id = newId
                }
            }
        } else {
            val (id, newBuffer) = createBuffer(capacityInBytes)
            copyOldBufferTo(newBuffer)
            this.buffer = newBuffer
            this.id = id
        }
    }

//    private fun delete() = gpuContext.calculate {
//        bind()
//        if (GL15.glGetBufferParameteri(target, GL15.GL_BUFFER_MAPPED) == GL11.GL_TRUE) {
//            GL15.glUnmapBuffer(target)
//        }
//        if (id > 0) {
//            GL15.glDeleteBuffers(id)
//            id = -1
//        }
//    }

    fun bind() {
        gpuContext.execute("PersistentMappedStructBuffer.bind") {
            if (id <= 0) {
                id = GL15.glGenBuffers()
            }
            GL15.glBindBuffer(target, id)
        }
    }

    fun unbind() {
        gpuContext.execute("PersistentMappedStructBuffer.unbind") { GL15.glBindBuffer(target, 0) }
    }

    @Synchronized
    fun resize(requestedCapacity: Int) {
        ensureCapacityInBytes(requestedCapacity * slidingWindow.sizeInBytes)
    }

    private fun createBuffer(capacityInBytes: Int): Pair<Int, ByteBuffer> = gpuContext.calculate {
        val id = GL15.glGenBuffers()
        GL15.glBindBuffer(target, id)
        GL44.glBufferStorage(target, capacityInBytes.toLong(), flags)
        val newBuffer = mapBuffer(capacityInBytes.toLong())
        Pair(id, newBuffer)
    }

    fun shrink(sizeInBytes: Int, copyContent: Boolean = true): PersistentMappedStructBuffer<T> {
        if(buffer.capacity() > sizeInBytes) {
            resize(sizeInBytes)
        }
        return this
    }

    fun enlarge(size: Int, copyContent: Boolean = true) = enlargeToBytes(size * slidingWindow.sizeInBytes, copyContent)

    fun enlargeBy(size: Int, copyContent: Boolean = true) = enlarge(this.size + size, copyContent)

    fun enlargeToBytes(sizeInBytes: Int, copyContent: Boolean = true) {
        ensureCapacityInBytes(sizeInBytes)
    }

    override operator fun get(index: Int): T {
        val currentSlidingWindow = slidingWindow
        currentSlidingWindow.localByteOffset = (index * currentSlidingWindow.sizeInBytes).toLong()
        return currentSlidingWindow.underlying
    }
    operator fun get(index: Int, slidingWindow: SlidingWindow<T>): T {
        slidingWindow.localByteOffset = (index * slidingWindow.sizeInBytes).toLong()
        return slidingWindow.underlying
    }

    fun addAll(elements: StructArray<T>) {
        val sizeBefore = size
        enlargeBy(elements.size)
        elements.buffer.copyTo(buffer, targetOffset = sizeBefore * slidingWindow.sizeInBytes)
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
