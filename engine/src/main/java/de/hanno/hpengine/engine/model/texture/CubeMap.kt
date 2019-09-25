package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP
import de.hanno.hpengine.engine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.util.Util
import org.lwjgl.opengl.EXTTextureCompressionS3TC
import org.lwjgl.opengl.EXTTextureSRGB
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL12
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface CubeTexture : Texture<TextureDimension3D>

open class CubeMap(protected val textureManager: TextureManager,
                   private val path: String,
                   override val dimension: TextureDimension3D,
                   override val textureFilterConfig: TextureFilterConfig = TextureFilterConfig(),
                   protected var srcPixelFormat: Int,
                   override val id: Int,
                   private val data: MutableList<ByteArray>) : CubeTexture, Serializable {
    private val srgba = true // TODO: Make this configurable
    override var handle: Long = -1
    override val target = TEXTURE_CUBE_MAP
    override val wrapMode = GL12.GL_CLAMP_TO_EDGE // TODO: Make this configurable
    override val internalFormat: Int = if (srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
    override var uploadState = UploadState.NOT_UPLOADED

    init {
        textureManager.gpuContext.bindTexture(this)
        setupTextureParameters()
    }

    fun setupTextureParameters() = textureManager.gpuContext.execute("setupTextureParameters") {
        glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, textureFilterConfig.minFilter.glValue)
        glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, textureFilterConfig.magFilter.glValue)
        glTexParameteri(target.glTarget, GL12.GL_TEXTURE_WRAP_R, wrapMode)
        glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, wrapMode)
        glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, wrapMode)
        glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0)
        glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, Util.calculateMipMapCount(Math.max(dimension.width, dimension.height)))
    }

    fun load(cubeMapFace: Int, buffer: ByteBuffer) {
        GL11.glTexImage2D(cubeMapFace,
                0,
                internalFormat,
                dimension.width / 4,
                dimension.height / 3,
                0,
                srcPixelFormat,
                GL11.GL_UNSIGNED_BYTE,
                buffer)
    }

    override fun toString(): String {
        return "(Cubemap)$path"
    }

    fun getData(): List<ByteArray> {
        return data
    }

    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun buffer(buffer: ByteBuffer, values: ByteArray): ByteBuffer {
            buffer.order(ByteOrder.nativeOrder())
            buffer.put(values, 0, values.size)
            buffer.flip()
            return buffer
        }
    }
}
