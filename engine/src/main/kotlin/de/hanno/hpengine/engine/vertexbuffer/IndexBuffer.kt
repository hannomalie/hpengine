package de.hanno.hpengine.engine.vertexbuffer

import de.hanno.struct.copyTo
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.AbstractPersistentMappedBuffer
import java.nio.IntBuffer
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.struct.StructArray
import org.lwjgl.opengl.GL15

open class IndexBuffer(gpuContext: GpuContext<*>?, target: Int) : AbstractPersistentMappedBuffer(
    gpuContext!!, target
) {
    constructor(gpuContext: GpuContext<*>?) : this(gpuContext, GL15.GL_ELEMENT_ARRAY_BUFFER) {
        ensureCapacityInBytes(4 * 3 * 50)
    }

    constructor(gpuContext: GpuContext<*>?, intBuffer: IntBuffer) : this(gpuContext) {
        put(intBuffer)
    }

    fun put(values: IntArray) {
        put(0, values)
    }

    fun put(offset: Int, values: IntArray) {
        ensureCapacityInBytes((values.size + offset) * Integer.BYTES)
        val intBuffer = buffer.asIntBuffer()
        intBuffer.position(offset)
        intBuffer.put(values)
        buffer.rewind()
    }

    /**
     *
     * @param offset
     * @param nonOffsetIndices indices as if no other indices were before in the index buffer
     */
    fun appendIndices(offset: Int, vararg nonOffsetIndices: Int) {
        buffer.rewind()
        ensureCapacityInBytes((nonOffsetIndices.size + offset) * Integer.BYTES)
        if (offset == 0) {
            put(nonOffsetIndices)
        } else {
            for (i in 0 until nonOffsetIndices.size) {
                put(offset + i, nonOffsetIndices[i])
            }
        }
    }

    fun put(indices: IntBuffer) {
        buffer.rewind()
        indices.rewind()
        buffer.asIntBuffer().put(indices)
    }

    fun put(index: Int, value: Int) {
        buffer.rewind()
        buffer.asIntBuffer().put(index, value)
    }

    val size: Int
        get() = sizeInBytes / Integer.BYTES

    fun appendIndices(indexOffset: Int, indices: StructArray<IntStruct>) {
        buffer.rewind()
        ensureCapacityInBytes((indexOffset + indices.size) * Integer.BYTES)
        indices.buffer.copyTo(buffer, true, indexOffset * Integer.BYTES)
    }
}