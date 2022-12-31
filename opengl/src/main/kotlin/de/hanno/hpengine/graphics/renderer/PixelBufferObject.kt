package de.hanno.hpengine.graphics.renderer

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.constants.glValue
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import java.nio.ByteBuffer

class PixelBufferObject(private val graphicsApi: GraphicsApi, private val width: Int, private val height: Int) {
    private val id: Int = GL15.glGenBuffers()
    private val buffer: ByteBuffer = BufferUtils.createByteBuffer(4 * 4 * width * height) // 4 is byte size of float
    private val array: FloatArray = FloatArray(4 * width * height)

    init {
        bind()
        GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, (4 * 4 * width * height).toLong(), GL15.GL_DYNAMIC_READ)
        unbind()
    }

    fun unbind() {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
    }

    fun bind() {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, id)
    }

    fun readPixelsFromTexture(textureId: Int, mipmapLevel: Int, target: TextureTarget, format: Int, type: Int) {
        bind()
        graphicsApi.bindTexture(target, textureId)
        GL11.glGetTexImage(target.glValue, mipmapLevel, format, type, buffer)
        unbind()
    }

    fun glTexSubImage2D(
        textureId: Int,
        mipmapLevel: Int,
        target: TextureTarget,
        format: Int,
        type: Int,
        width: Int,
        height: Int,
        buffer: ByteBuffer?
    ) {
        glTexSubImage2D(textureId, mipmapLevel, target, format, type, 0, 0, width, height, buffer)
    }

    fun glTexSubImage2D(
        textureId: Int,
        mipmapLevel: Int,
        target: TextureTarget,
        format: Int,
        type: Int,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int,
        buffer: ByteBuffer?
    ) {
        mapAndUnmap(offsetX, offsetY, width, height, buffer)
        graphicsApi.onGpu {
            graphicsApi.bindTexture(target, textureId)
            GL11.glTexSubImage2D(
                target.glValue,
                mipmapLevel,
                offsetX,
                offsetY,
                width,
                height,
                GL11.GL_RGBA,
                GL11.GL_FLOAT,
                0
            )
            Unit
        }
        unbind()
    }

    fun glCompressedTexImage2D(
        textureId: Int,
        target: TextureTarget,
        level: Int,
        internalformat: Int,
        width: Int,
        height: Int,
        border: Int,
        textureBuffer: ByteBuffer?
    ) {
        graphicsApi.onGpu {
            mapAndUnmap(0, 0, width, height, buffer)
            graphicsApi.bindTexture(target, textureId)
            GL13.glCompressedTexImage2D(target.glValue, level, internalformat, width, height, border, null)
            Unit
        }
        unbind()
    }

    private fun mapAndUnmap(offsetX: Int, offsetY: Int, width: Int, height: Int, buffer: ByteBuffer?) {
        bind()
        //		glBufferData(GL_PIXEL_UNPACK_BUFFER, 4*4*(width-offsetX)*(height*offsetY), GL_STREAM_COPY);
//		ByteBuffer result = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_READ_WRITE, buffer);
        val result = GL30.glMapBufferRange(
            GL21.GL_PIXEL_UNPACK_BUFFER,
            0,
            (4 * 4 * width * height).toLong(),
            GL30.GL_MAP_READ_BIT,
            buffer
        )
        result!!.put(buffer)
        if (GL15.glGetBufferParameteri(GL21.GL_PIXEL_UNPACK_BUFFER, GL15.GL_BUFFER_MAPPED) == GL11.GL_TRUE) {
            GL15.glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER)
        }
    }

    fun mapBuffer(): FloatArray {
        GL15.glMapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, GL15.GL_READ_WRITE, buffer)
        buffer.rewind()
        buffer.asFloatBuffer()[array]
        return array
    }
}