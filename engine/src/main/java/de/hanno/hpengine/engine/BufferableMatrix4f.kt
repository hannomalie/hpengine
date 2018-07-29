package de.hanno.hpengine.engine

import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import org.joml.Matrix4f
import java.lang.Float
import java.nio.ByteBuffer

class BufferableMatrix4f : Matrix4f, Bufferable {
    constructor(localJointMatrix: Matrix4f) : super(localJointMatrix)

    override fun putToBuffer(buffer: ByteBuffer?) {
        buffer?.let {
            with(buffer) {
                putFloat(m00())
                putFloat(m01())
                putFloat(m02())
                putFloat(m03())
                putFloat(m10())
                putFloat(m11())
                putFloat(m12())
                putFloat(m13())
                putFloat(m20())
                putFloat(m21())
                putFloat(m22())
                putFloat(m23())
                putFloat(m30())
                putFloat(m31())
                putFloat(m32())
                putFloat(m33())
            }
        }
    }

    override fun getBytesPerObject() = getBytesPerInstance()

    companion object {
        fun getBytesPerInstance() = Float.BYTES * 16
    }
}