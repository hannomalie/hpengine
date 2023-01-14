package de.hanno.hpengine.graphics.texture


import de.hanno.hpengine.graphics.GraphicsApi
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL21.*
import java.nio.ByteBuffer

context(GraphicsApi)
class PixelBufferObject(val id: Int) {

    companion object {
        context(GraphicsApi)
        operator fun invoke() = PixelBufferObject(onGpu { glGenBuffers() })
    }

    fun put(data: ByteBuffer) {
        val buffer = onGpu {
            GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, id)
            GL15.glBufferData(GL_PIXEL_UNPACK_BUFFER, data.capacity().toLong(), GL15.GL_STREAM_COPY)
            val buffer = GL15.glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL15.GL_WRITE_ONLY, null)!!
            buffer.rewind()
            buffer
        }

        buffer.put(data)
        unmap()
    }

    fun unmap() = onGpu {
        bind()
        GL15.glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER)
    }

    fun bind() = onGpu {
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, id)
    }
    fun unbind() = onGpu {
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)
    }
}