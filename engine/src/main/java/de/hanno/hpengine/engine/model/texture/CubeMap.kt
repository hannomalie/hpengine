package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP
import org.lwjgl.opengl.EXTTextureSRGB
import org.lwjgl.opengl.GL11
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface CubeTexture: Texture

open class CubeMap(protected val textureManager: TextureManager, private val path: String, override val width: Int, override val height: Int, override val minFilter: Int, override val magFilter: Int, protected var srcPixelFormat: Int, protected var dstPixelFormat: Int, override val textureId: Int, private val data: MutableList<ByteArray>) : CubeTexture, Serializable {
    override var handle: Long = 0
    override var lastUsedTimeStamp: Long = 0
        protected set
    override val target = TEXTURE_CUBE_MAP

    fun load(cubeMapFace: Int, buffer: ByteBuffer) {
        GL11.glTexImage2D(cubeMapFace,
                0,
                EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT,
                width / 4,
                height / 3,
                0,
                srcPixelFormat,
                GL11.GL_UNSIGNED_BYTE,
                buffer)
    }

    override fun toString(): String {
        return "(Cubemap)$path"
    }

    override fun setUsedNow() {
        //		TODO Implement me
    }

    fun getData(): List<ByteArray> {
        return data
    }

    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic fun buffer(buffer: ByteBuffer, values: ByteArray): ByteBuffer {
            buffer.order(ByteOrder.nativeOrder())
            buffer.put(values, 0, values.size)
            buffer.flip()
            return buffer
        }
    }
}
