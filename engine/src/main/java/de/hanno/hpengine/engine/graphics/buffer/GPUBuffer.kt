package de.hanno.hpengine.engine.graphics.buffer

import java.nio.ByteBuffer
import java.nio.IntBuffer

interface GPUBuffer {

    val buffer: ByteBuffer

    val id: Int

    var sizeInBytes: Int
    fun ensureCapacityInBytes(requestedCapacity: Int) {
        throw IllegalStateException("NOT IMPLEMENTED")
    }

    fun bind()

    fun unbind()

    @JvmDefault
    fun putValues(values: ByteBuffer) {
        putValues(0, values)
    }

    @JvmDefault
    fun putValues(byteOffset: Int, values: ByteBuffer) {
        if (values.capacity() > sizeInBytes) {
            sizeInBytes = values.capacity()
        }
        bind()
        values.rewind()
        buffer.rewind()
        buffer.position(byteOffset)
        buffer.put(values)
        buffer.rewind()
    }

    @JvmDefault
    fun putValues(vararg values: Float) {
        putValues(0, *values)
    }

    fun putValues(offset: Int, vararg values: Float) {
        bind()
        buffer.position(offset)
        val doubleValues = DoubleArray(values.size)
        for (i in values.indices) {
            doubleValues[i] = values[i].toDouble()
        }
        buffer.asDoubleBuffer().put(doubleValues)
    }

}
