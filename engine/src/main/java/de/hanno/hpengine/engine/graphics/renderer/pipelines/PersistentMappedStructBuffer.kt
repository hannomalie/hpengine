package de.hanno.hpengine.engine.graphics.renderer.pipelines

import DrawElementsIndirectCommandStruktImpl.Companion.type
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.flags
import de.hanno.struct.Array
import de.hanno.struct.SlidingWindow
import de.hanno.struct.Struct
import de.hanno.struct.StructArray
import de.hanno.struct.copyTo
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL40
import org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER
import org.lwjgl.opengl.GL43
import org.lwjgl.opengl.GL44
import struktgen.TypedBuffer
import struktgen.api.Strukt
import struktgen.api.StruktType
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

interface Buffer {
    val buffer: ByteBuffer
}

interface GpuBuffer: Buffer {
    val target: Int
    val id: Int
    override val buffer: ByteBuffer
}
interface TypedGpuBuffer<T>: GpuBuffer {
    val typedBuffer: TypedBuffer<T>
}


interface Allocator<T: Buffer> {
    fun allocate(capacityInBytes: Int) = allocate(capacityInBytes, null)
    fun allocate(capacityInBytes: Int, current: T?): T
}

class PersistentMappedBufferAllocator(
    val gpuContext: GpuContext<*>,
    val target: Int = GL43.GL_SHADER_STORAGE_BUFFER
): Allocator<GpuBuffer> {

    override fun allocate(capacityInBytes: Int, current: GpuBuffer?): GpuBuffer = gpuContext.window.invoke {
        require(capacityInBytes > 0) { "Cannot allocate buffer of size 0!" }
        val id = GL15.glGenBuffers()
        GL15.glBindBuffer(target, id)
        GL44.glBufferStorage(target, capacityInBytes.toLong(), flags)
        val newBuffer = mapBuffer(capacityInBytes.toLong(), current)

        object: GpuBuffer {
            override val target = this@PersistentMappedBufferAllocator.target
            override val id = id
            override val buffer = newBuffer
        }
    }

    fun mapBuffer(capacityInBytes: Long, oldBuffer: GpuBuffer?): ByteBuffer {
        require(capacityInBytes > 0) { "Cannot map buffer with 0 capacity!" }
//            TODO: This causes segfaults in Unsafe class, wtf...
        val xxxx = BufferUtils.createByteBuffer(capacityInBytes.toInt())
        val byteBuffer = GL30.glMapBufferRange(
            target,
            0, capacityInBytes, flags,
//                null)!!
            xxxx
        )!!

        oldBuffer?.let { oldBuffer ->
            val array = ByteArray(oldBuffer.buffer.capacity())
            oldBuffer.buffer.rewind()
            oldBuffer.buffer.get(array)
//            byteBuffer.put(buffer)
            byteBuffer.put(array)
            byteBuffer.rewind()
        }
        return byteBuffer
    }

    @Synchronized // TODO: Question this
    fun ensureCapacityInBytes(oldBuffer: GpuBuffer?, requestedCapacity: Int): GpuBuffer {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= 0) {
            capacityInBytes = 10
        }

        return if (oldBuffer != null) {
            val needsResize = oldBuffer.buffer.capacity() < capacityInBytes
            if (needsResize) {
                val newBuffer = allocate(capacityInBytes, oldBuffer)
                gpuContext.invoke {
                    GL15.glDeleteBuffers(oldBuffer.id)
                }
                newBuffer
            } else oldBuffer
        } else {
            allocate(capacityInBytes, null)
        }
    }
}


class PersistentMappedStructBuffer<T : Struct>(
    initialSize: Int,
    val gpuContext: GpuContext<*>,
    val factory: () -> T,
    override val target: Int = GL43.GL_SHADER_STORAGE_BUFFER
) : Array<T>, GpuBuffer {

    var slidingWindow = createSlidingWindow()
        private set

    fun createSlidingWindow(): SlidingWindow<T> {
        return SlidingWindow(factory()).apply {
            underlying.provideBuffer = { buffer }
        }
    }

    override lateinit var buffer: ByteBuffer

    override val size
        get() = buffer.capacity() / slidingWindow.sizeInBytes

    override var id: Int = -1
        private set

    init {
        gpuContext.window.invoke {
            val (id, newBuffer) = createBuffer(max(initialSize * slidingWindow.sizeInBytes, 1))
            this.buffer = newBuffer
            this.id = id
        }
    }

    override val indices
        get() = 0 until size

    override val sizeInBytes: Int
        get() = size * slidingWindow.sizeInBytes

    fun mapBuffer(capacityInBytes: Long): ByteBuffer {
//            TODO: This causes segfaults in Unsafe class, wtf...
        val xxxx = BufferUtils.createByteBuffer(capacityInBytes.toInt());
        val byteBuffer = GL30.glMapBufferRange(
            target,
            0, capacityInBytes, flags,
//                null)!!
            xxxx
        )!!
        copyOldBufferTo(byteBuffer)
        return byteBuffer
    }

    private fun copyOldBufferTo(byteBuffer: ByteBuffer) {
        if (::buffer.isInitialized) {
            val array = ByteArray(min(byteBuffer.capacity(), buffer.capacity()))
            buffer.rewind()
            buffer.get(array)
//            byteBuffer.put(buffer)
            byteBuffer.put(array)
            byteBuffer.rewind()
        }
    }

    @Synchronized
    fun setCapacityInBytes(requestedCapacity: Int) {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= 0) {
            capacityInBytes = 10
        }

        if (::buffer.isInitialized) {
            val needsResize = buffer.capacity() != capacityInBytes
            if (needsResize) {
                gpuContext.invoke {
                    val (newId, newBuffer) = createBuffer(capacityInBytes)
                    val oldId = this.id
                    GL15.glDeleteBuffers(oldId)
                    this.buffer = newBuffer
                    this.slidingWindow = createSlidingWindow()
                    this.id = newId
                }
            }
        } else {
            val (id, newBuffer) = createBuffer(capacityInBytes)
            this.buffer = newBuffer
            this.slidingWindow = createSlidingWindow()
            this.id = id
        }
    }
    @Synchronized
    fun ensureCapacityInBytes(requestedCapacity: Int) {
        var capacityInBytes = requestedCapacity
        if (capacityInBytes <= 0) {
            capacityInBytes = 10
        }

        if (::buffer.isInitialized) {
            val needsResize = buffer.capacity() < capacityInBytes
            if (needsResize) {
                gpuContext.invoke {
                    val (newId, newBuffer) = createBuffer(capacityInBytes)
                    copyOldBufferTo(newBuffer)
                    val oldId = this.id
                    GL15.glDeleteBuffers(oldId)
                    this.buffer = newBuffer
                    this.slidingWindow = createSlidingWindow()
                    this.id = newId
                }
            }
        } else {
            val (id, newBuffer) = createBuffer(capacityInBytes)
            copyOldBufferTo(newBuffer)
            this.buffer = newBuffer
            this.slidingWindow = createSlidingWindow()
            this.id = id
        }
    }

    fun bind() {
        gpuContext.invoke {
            if (id <= 0) {
                id = GL15.glGenBuffers()
            }
            GL15.glBindBuffer(target, id)
        }
    }

    fun unbind() {
        gpuContext.invoke { GL15.glBindBuffer(target, 0) }
    }

    @Synchronized
    fun resize(requestedCapacity: Int) {
        ensureCapacityInBytes(requestedCapacity * slidingWindow.sizeInBytes)
    }

    private fun createBuffer(capacityInBytes: Int): Pair<Int, ByteBuffer> = gpuContext.invoke {
        val id = GL15.glGenBuffers()
        GL15.glBindBuffer(target, id)
        GL44.glBufferStorage(target, capacityInBytes.toLong(), flags)
        val newBuffer = mapBuffer(capacityInBytes.toLong())
        Pair(id, newBuffer)
    }

    fun shrink(sizeInBytes: Int, copyContent: Boolean = true): PersistentMappedStructBuffer<T> {
        if (buffer.capacity() > sizeInBytes) {
            resize(sizeInBytes)
        }
        return this
    }

    fun enlarge(size: Int, copyContent: Boolean = true) = enlargeToBytes(size * slidingWindow.sizeInBytes, copyContent)

    fun enlargeBy(size: Int, copyContent: Boolean = true) = enlarge(this.size + size, copyContent)

    fun enlargeByBytes(sizeInBytes: Int, copyContent: Boolean = true) = enlarge(this.size + (sizeInBytes + slidingWindow.sizeInBytes), copyContent)

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
    fun addAll(elements: ByteBuffer) {
        val sizeBefore = size
        enlargeByBytes(elements.capacity())
        elements.copyTo(buffer, targetOffset = sizeBefore * slidingWindow.sizeInBytes)
    }
}

@JvmOverloads
fun <T : Struct> Array<T>.safeCopyTo(target: PersistentMappedStructBuffer<T>, rewindBuffers: Boolean = true) {
    target.resize(size)
    copyTo(target, rewindBuffers)
}

fun CommandBuffer(
    gpuContext: GpuContext<*>,
    size: Int = 1000
) = PersistentMappedBuffer( size * DrawElementsIndirectCommandStrukt.type.sizeInBytes, gpuContext, GL_DRAW_INDIRECT_BUFFER).typed(DrawElementsIndirectCommandStrukt.type)

class IntStruct : Struct() {
    var value by 0
    override fun toString() = "$value"
}
interface IntStrukt: Strukt {
    context(ByteBuffer) var value: Int
    companion object
}

fun IndexBuffer(gpuContext: GpuContext<*>, size: Int = 1000) = PersistentMappedStructBuffer(size, gpuContext, { IntStruct() }, GL40.GL_ELEMENT_ARRAY_BUFFER)

data class PersistentTypedBuffer<T>(val persistentMappedBuffer: PersistentMappedBuffer, val type: StruktType<T>): GpuBuffer by persistentMappedBuffer, TypedGpuBuffer<T> {
    override val typedBuffer = object: TypedBuffer<T>(type) {
        override val byteBuffer: ByteBuffer
            get() = persistentMappedBuffer.buffer
    }
    override val buffer: ByteBuffer
        get() = persistentMappedBuffer.buffer

    @Synchronized
    fun ensureCapacityInBytes(requestedCapacity: Int) = persistentMappedBuffer.ensureCapacityInBytes(requestedCapacity)
    @Synchronized
    fun resize(requestedCapacity: Int) = persistentMappedBuffer.resize(requestedCapacity)

    fun addAll(offset: Int? = null, elements: TypedBuffer<T>) = addAll(offset, elements.byteBuffer)
    fun addAll(offset: Int? = null, elements: ByteBuffer) {
        val offset = offset ?: buffer.capacity()
        ensureCapacityInBytes(offset + elements.capacity())
        elements.copyTo(buffer, rewindBuffers = true, targetOffset = offset)
    }
}

fun <T> PersistentMappedBuffer.typed(type: StruktType<T>) = PersistentTypedBuffer(this, type)

class PersistentMappedBuffer(
    initialSizeInBytes: Int,
    private val gpuContext: GpuContext<*>,
    _target: Int = GL43.GL_SHADER_STORAGE_BUFFER
): GpuBuffer {

    private val allocator = PersistentMappedBufferAllocator(gpuContext, _target)

    private var gpuBuffer: GpuBuffer = allocator.allocate(initialSizeInBytes, null)

    override val buffer: ByteBuffer
        get() = gpuBuffer.buffer
    override val id: Int
        get() = gpuBuffer.id
    override val target: Int
        get() = gpuBuffer.target

    val sizeInBytes: Int
        get() = buffer.capacity()

    @Synchronized
    fun ensureCapacityInBytes(requestedCapacity: Int) {
        gpuBuffer = allocator.ensureCapacityInBytes(gpuBuffer, requestedCapacity)
    }

    fun bind() {
        gpuContext.invoke {
            GL15.glBindBuffer(target, id)
        }
    }

    fun unbind() {
        gpuContext.invoke { GL15.glBindBuffer(target, 0) }
    }

    @Synchronized
    fun resize(requestedCapacityInBytes: Int) {
        ensureCapacityInBytes(requestedCapacityInBytes)
    }

    fun enlarge(sizeInBytes: Int, copyContent: Boolean = true) = enlargeToBytes(sizeInBytes, copyContent)

    fun enlargeBy(sizeInBytes: Int, copyContent: Boolean = true) = enlarge(this.sizeInBytes + sizeInBytes, copyContent)

    fun enlargeToBytes(sizeInBytes: Int, copyContent: Boolean = true) {
        ensureCapacityInBytes(sizeInBytes)
    }
}
