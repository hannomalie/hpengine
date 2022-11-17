package de.hanno.hpengine.graphics.texture


import de.hanno.hpengine.graphics.GpuContext
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL21.*
import java.nio.ByteBuffer

class PixelBufferObject(val id: Int) {

    companion object {
        operator fun invoke(): PixelBufferObject {
            val pbo = GL15.glGenBuffers()
            return PixelBufferObject(pbo)
        }
    }

    fun put(gpuContext: GpuContext, data: ByteBuffer) {

        val buffer = gpuContext.onGpu {
            GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, id)
            GL15.glBufferData(GL_PIXEL_UNPACK_BUFFER, data.capacity().toLong(), GL15.GL_STREAM_COPY)
            val buffer = GL15.glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL15.GL_WRITE_ONLY, null)!!
            buffer.rewind()
            buffer
        }

        buffer.put(data)
        unmap(gpuContext)
    }

    fun unmap(gpuContext: GpuContext) {
        gpuContext.onGpu {
            bind()
            val isMapped = GL15.glGetBufferParameteri(GL_PIXEL_UNPACK_BUFFER, GL15.GL_BUFFER_MAPPED) == GL11.GL_TRUE
            val zeroIsBound = GL11.glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING) == 0
            if (isMapped && !zeroIsBound) {
                GL15.glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER)
            }
        }
    }

    fun bind() {
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, id)
    }
    fun unbind() {
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)
    }
}