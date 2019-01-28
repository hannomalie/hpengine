package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.graphics.GpuContext
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL21
import java.nio.ByteBuffer

class PixelBufferObject(val id: Int) {

    companion object {
        operator fun invoke(): PixelBufferObject {
            val pbo = GL15.glGenBuffers()
            return PixelBufferObject(pbo)
        }
    }

    fun put(gpuContext: GpuContext, data: ByteBuffer) {

        val buffer = gpuContext.calculate {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, id)
            GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, data.capacity().toLong(), GL15.GL_STREAM_COPY)
            val buffer = GL15.glMapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, GL15.GL_WRITE_ONLY, null)!!
            buffer.rewind()
            buffer
        }

        buffer.put(data)
        gpuContext.execute {
            GL15.glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER)
        }
    }
    fun bind() {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, id)
    }
    fun unbind() {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
    }
}