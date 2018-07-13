package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP
import org.lwjgl.opengl.EXTTextureSRGB
import org.lwjgl.opengl.GL11
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface CubeTexture: ITexture<List<ByteArray>>

open class CubeMap(protected val textureManager: TextureManager, private val path: String, override val width: Int, override val height: Int, override val minFilter: Int, override val magFilter: Int, protected var srcPixelFormat: Int, protected var dstPixelFormat: Int, override val textureId: Int, data: MutableList<ByteArray>) : CubeTexture, Serializable {
    override var handle: Long = 0
    override var lastUsedTimeStamp: Long = 0
        protected set
    private val data = data
    override val target = TEXTURE_CUBE_MAP

    fun buffer(buffer: ByteBuffer, values: ByteArray): ByteBuffer {
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(values, 0, values.size)
        buffer.flip()
        return buffer
    }

    fun load(cubemapFace: Int, buffer: ByteBuffer) {
        GL11.glTexImage2D(cubemapFace,
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

    fun bind(gpuContext: GpuContext, unit: Int) {
        gpuContext.bindTexture(unit, TEXTURE_CUBE_MAP, textureId)
    }

    internal fun bind() {
        textureManager.gpuContext.bindTexture(target, textureId)
    }

    @JvmOverloads fun bind(textureUnitIndex: Int = 0) {
        textureManager.gpuContext.bindTexture(textureUnitIndex, target, textureId)
    }

    override fun setUsedNow() {
        //		TODO Implement me
    }

    override fun getData(): List<ByteArray> {
        return data
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
