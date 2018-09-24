package de.hanno.hpengine.engine.model.texture

import ddsutil.DDSUtil
import ddsutil.ImageRescaler
import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.model.texture.UploadState.NOT_UPLOADED
import de.hanno.hpengine.engine.model.texture.UploadState.UPLOADED
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.ressources.Reloadable
import jogl.DDSImage
import org.apache.commons.io.FilenameUtils
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
import org.lwjgl.opengl.EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL44.glClearTexImage
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel

open class PathBasedOpenGlTexture(protected val textureManager: TextureManager,
                                  target: GlTextureTarget,
                                  val path: String,
                                  val srgba: Boolean,
                                  width: Int,
                                  height: Int,
                                  depth: Int = if (target.is3D) 1 else 0,
                                  mipmapCount: Int,
                                  val srcPixelFormat: Int,
                                  textureId: Int,
                                  minFilter: Int = GL11.GL_LINEAR_MIPMAP_LINEAR,
                                  magFilter: Int = GL11.GL_LINEAR,
                                  wrapMode: Int = GL_REPEAT,
                                  val backingTexture: OpenGlTexture = OpenGlTexture(textureManager, target, srgba, width = width, height = height, depth = depth, mipmapCount = mipmapCount, textureId = textureId, name = path, minFilter = minFilter, magFilter = magFilter, wrapMode = wrapMode, uploadState = NOT_UPLOADED)) : Reloadable, Texture by backingTexture  {

    var preventUnload = false

    override fun load() = with(textureManager) {
        upload(getCompleteTextureInfo(path, srgba))
    }

    override fun unload() = with(textureManager) {
        unloadTexture()
    }

    override fun toString() = "(Texture)$path"

    override fun getName() = path
}


open class OpenGlTexture(protected val textureManager: TextureManager,
                         override val target: GlTextureTarget = TEXTURE_2D,
                         srgba: Boolean = false,
                         override val internalFormat: Int = if (srgba) GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else GL_COMPRESSED_RGBA_S3TC_DXT5_EXT,
                         override val width: Int,
                         override val height: Int,
                         override val depth: Int = if (target.is3D) 1 else 0,
                         val mipmapCount: Int = Util.calculateMipMapCount(width, height),
                         override val textureId: Int = textureManager.gpuContext.genTextures(),
                         val name: String = "OpenGlTexture-$textureId",
                         override val minFilter: Int = GL11.GL_LINEAR_MIPMAP_LINEAR,
                         override val magFilter: Int = GL11.GL_LINEAR,
                         override val wrapMode: Int = GL_REPEAT,
                         override var uploadState: UploadState = UPLOADED) : Texture {

    override var handle = -1L

    override fun toString() = "(Texture)$name"

    init {
        with(textureManager) {
//            gpuContext.execute {
                gpuContext.bindTexture(this@OpenGlTexture)

//                texStorage(target, internalFormat, width, height, depth, mipmapCount)
//                texSubImage(target, internalFormat, width, height, depth)
                texImage(target, 0, internalFormat, width, height, depth)
                generateMipMaps(target, textureId)

                setupTextureParameters()
                createTextureHandleAndMakeResident()
                if(handle <= 0) {
                    GpuContext.exitOnGLError("Handle of texture $name is $handle, means texture is not complete!")
                }
            }
//        }
    }

    private fun setupTextureParameters() = textureManager.gpuContext.execute {
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, minFilter)
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, magFilter)
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_WRAP_R, wrapMode)
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, wrapMode)
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, wrapMode)
//         TODO: Maybe remove this
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0)
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, mipmapCount+1)
    }
    companion object {
        private val LOGGER = Logger.getLogger(OpenGlTexture::class.java.name)
        fun buffer(data: ByteArray): ByteBuffer {
            val imageBuffer = BufferUtils.createByteBuffer(data.size)
            imageBuffer.put(data, 0, data.size)
            imageBuffer.flip()
            return imageBuffer
        }

        const val directory = DirectoryManager.WORKDIR_NAME + "/assets/textures/"

        @JvmStatic
        fun textureAvailableAsDDS(resourceName: String): Boolean {
            val fullPathAsDDS = getFullPathAsDDS(resourceName)
            val f = File(fullPathAsDDS)
            return f.exists()
        }

        @JvmStatic
        fun getFullPathAsDDS(fileName: String): String {
            val name = FilenameUtils.getBaseName(fileName)
            val nameWithExtension = FilenameUtils.getName(fileName)//FilenameUtils.getName(fileName) + "." + extension;
            var restPath: String? = fileName.replace(nameWithExtension.toRegex(), "")
            if (restPath == null) {
                restPath = ""
            }
            return "$restPath$name.dds"
        }

        @JvmStatic
        protected fun getNextPowerOfTwo(fold: Int): Int {
            var ret = 2
            while (ret < fold) {
                ret *= 2
            }
            return ret
        }

        private val DDSUtilWriteLock = ReentrantLock()
        const val autoConvertToDDS = true

        private var counter = 14
        private fun showAsTextureInFrame(bufferedImage: BufferedImage) {
            if (counter-- < 0) {
                return
            }
            val frame = JFrame("WINDOW")
            frame.isVisible = true
            frame.add(JLabel(ImageIcon(bufferedImage)))
            frame.pack()
        }

        @Throws(IOException::class)
        @JvmStatic
        fun saveAsDDS(path: String, bufferedImage: BufferedImage): BufferedImage {
            val start = System.currentTimeMillis()
            val rescaledImage = rescaleToNextPowerOfTwo(bufferedImage)
            val ddsFile = File(getFullPathAsDDS(path))
            if (ddsFile.exists()) {
                ddsFile.delete()
            }
            DDSUtilWriteLock.lock()
            try {
                DDSUtil.write(ddsFile, rescaledImage, DDSImage.D3DFMT_DXT5, true)
            } finally {
                DDSUtilWriteLock.unlock()
            }
            LOGGER.info("Converting and saving as dds took " + (System.currentTimeMillis() - start) + "ms")
            return rescaledImage
        }

        @JvmStatic
        fun rescaleToNextPowerOfTwo(nonPowerOfTwoImage: BufferedImage): BufferedImage {
            val oldWidth = nonPowerOfTwoImage.width
            val nextPowerOfTwoWidth = getNextPowerOfTwo(oldWidth)
            val oldHeight = nonPowerOfTwoImage.height
            val nextPowerOfTwoHeight = getNextPowerOfTwo(oldHeight)
            var result = nonPowerOfTwoImage

            val maxOfWidthAndHeightPoT = Math.max(nextPowerOfTwoWidth, nextPowerOfTwoHeight)

            if (oldWidth != nextPowerOfTwoWidth || oldHeight != nextPowerOfTwoHeight) {
                result = ImageRescaler().rescaleBI(nonPowerOfTwoImage, maxOfWidthAndHeightPoT, maxOfWidthAndHeightPoT)
                LOGGER.info("Image rescaled from " + oldWidth + " x " + oldHeight + " to " + result.width + " x " + result.height)
            }
            return result
        }

        @JvmStatic
        fun buffer(data: Array<ByteArray>): ByteBuffer {
            val imageBuffer = BufferUtils.createByteBuffer(data[0].size)
            imageBuffer.put(data[0], 0, data[0].size)
            imageBuffer.flip()
            return imageBuffer
        }

        @JvmStatic
        fun buffer(data: Array<Future<ByteArray>>): ByteBuffer {
            val actualData = data[0].get()
            val imageBuffer = BufferUtils.createByteBuffer(actualData.size)
            imageBuffer.put(actualData, 0, actualData.size)
            imageBuffer.flip()
            return imageBuffer
        }

        @JvmStatic
        fun bufferLayer(data: ByteArray): ByteBuffer {
            val imageBuffer = ByteBuffer.allocateDirect(data.size)
            imageBuffer.put(data, 0, data.size)
            imageBuffer.flip()
            return imageBuffer
        }

    }

}

val Int.isCompressed: Boolean
    get() = this == GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT || this == GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
