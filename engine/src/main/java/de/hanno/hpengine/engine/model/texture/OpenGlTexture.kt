package de.hanno.hpengine.engine.model.texture

import ddsutil.DDSUtil
import ddsutil.ImageRescaler
import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.model.texture.OpenGlTexture.UploadState.*
import de.hanno.hpengine.util.ressources.Reloadable
import jogl.DDSImage
import org.apache.commons.io.FilenameUtils
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
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

open class OpenGlTexture internal constructor(protected val textureManager: TextureManager,
                                              val path: String,
                                              val srgba: Boolean,
                                              override val width: Int,
                                              override val height: Int,
                                              val mipmapCount: Int,
                                              val srcPixelFormat: Int,
                                              override val textureId: Int,
                                              override val minFilter: Int = GL11.GL_LINEAR,
                                              override val magFilter: Int = GL11.GL_LINEAR) : Reloadable, Texture {
    override val target = TEXTURE_2D
    override var lastUsedTimeStamp = System.currentTimeMillis()
    @Volatile var preventUnload = false

    @Volatile var uploadState = NOT_UPLOADED
        internal set

    override var handle = -1L

    override fun setUsedNow() {
        if (NOT_UPLOADED == uploadState) {
            load()
        } else if (UPLOADED == uploadState || UPLOADING == uploadState) {
            lastUsedTimeStamp = System.currentTimeMillis()
        }
    }

    override fun toString() = "(Texture)$path"

    override fun getName() = path

    override fun load() = with(textureManager) {
        upload(textureManager.getCompleteTextureInfo(path, srgba))
    }

    override fun unload() = with(textureManager) {
        unloadTexture()
    }

    enum class UploadState {
        NOT_UPLOADED,
        UPLOADING,
        UPLOADED
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

        @JvmStatic fun filterRequiresMipmaps(magTextureFilter: Int): Boolean {
            return magTextureFilter == GL11.GL_LINEAR_MIPMAP_LINEAR ||
                    magTextureFilter == GL11.GL_LINEAR_MIPMAP_NEAREST ||
                    magTextureFilter == GL11.GL_NEAREST_MIPMAP_LINEAR ||
                    magTextureFilter == GL11.GL_NEAREST_MIPMAP_NEAREST
        }

        @JvmStatic fun textureAvailableAsDDS(resourceName: String): Boolean {
            val fullPathAsDDS = getFullPathAsDDS(resourceName)
            val f = File(fullPathAsDDS)
            return f.exists()
        }

        @JvmStatic fun getFullPathAsDDS(fileName: String): String {
            val name = FilenameUtils.getBaseName(fileName)
            val nameWithExtension = FilenameUtils.getName(fileName)//FilenameUtils.getName(fileName) + "." + extension;
            var restPath: String? = fileName.replace(nameWithExtension.toRegex(), "")
            if (restPath == null) {
                restPath = ""
            }
            return "$restPath$name.dds"
        }

        @JvmStatic protected fun getNextPowerOfTwo(fold: Int): Int {
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
        @JvmStatic fun saveAsDDS(path: String, bufferedImage: BufferedImage): BufferedImage {
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

        @JvmStatic fun rescaleToNextPowerOfTwo(nonPowerOfTwoImage: BufferedImage): BufferedImage {
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

        @JvmStatic fun buffer(data: Array<ByteArray>): ByteBuffer {
            val imageBuffer = BufferUtils.createByteBuffer(data[0].size)
            imageBuffer.put(data[0], 0, data[0].size)
            imageBuffer.flip()
            return imageBuffer
        }
        @JvmStatic fun buffer(data: Array<Future<ByteArray>>): ByteBuffer {
            val actualData = data[0].get()
            val imageBuffer = BufferUtils.createByteBuffer(actualData.size)
            imageBuffer.put(actualData, 0, actualData.size)
            imageBuffer.flip()
            return imageBuffer
        }
        @JvmStatic fun bufferLayer(data: ByteArray): ByteBuffer {
            val imageBuffer = ByteBuffer.allocateDirect(data.size)
            imageBuffer.put(data, 0, data.size)
            imageBuffer.flip()
            return imageBuffer
        }

    }
}
