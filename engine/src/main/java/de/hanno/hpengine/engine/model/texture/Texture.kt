package de.hanno.hpengine.engine.model.texture

import ddsutil.DDSUtil
import ddsutil.ImageRescaler
import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.ressources.Reloadable
import jogl.DDSImage
import org.apache.commons.io.FilenameUtils
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*

import javax.swing.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger

import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.model.texture.Texture.UploadState.*
import org.lwjgl.opengl.GL15.GL_STREAM_COPY
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER

//TODO: Remove leading I
interface ITexture<T> {
    val width: Int
    val height: Int
    val textureId: Int
    var target: GlTextureTarget
    val handle: Long
    val lastUsedTimeStamp: Long
    val minFilter: Int
    val magFilter: Int
    fun unload() {} //TODO: Remove this maybe?
    fun setUsedNow() { }
    fun getData(): T
}
interface CubeTexture: ITexture<List<ByteArray>>

open class Texture internal constructor(protected val textureManager: TextureManager, path: String, val srgba: Boolean, override val width: Int, override val height: Int, private val mipmapCount: Int, mipmapsGenerated: Boolean, protected val srcPixelFormat: Int, sourceDataCompressed: Boolean, protected val data: Array<ByteArray>, override val textureId: Int) : Reloadable, ITexture<ByteArray> {
    var path = ""
    private val mipmapsGenerated: Boolean
    private val sourceDataCompressed: Boolean
    override var lastUsedTimeStamp = System.currentTimeMillis()
    @Volatile
    private var preventUnload = false

    @Volatile
    var uploadState = NOT_UPLOADED
        private set

    override var target = TEXTURE_2D
    protected val dstPixelFormat: Int = 0
    override val minFilter: Int = 0
    override val magFilter: Int = 0
    override var handle = -1L
        protected set

    override fun setUsedNow() {
        if (NOT_UPLOADED == uploadState) {
            load()
        } else if (UPLOADED == uploadState || UPLOADING == uploadState) {
            lastUsedTimeStamp = System.currentTimeMillis()
        }
    }

    init {
        this.target = TEXTURE_2D
        this.path = path
        this.mipmapsGenerated = mipmapsGenerated
        this.sourceDataCompressed = sourceDataCompressed

    }

    protected fun genHandle(textureManager: TextureManager) {
        if (handle <= 0) {
            handle = textureManager.gpuContext.calculate {
                bind(15)
                val theHandle = ARBBindlessTexture.glGetTextureHandleARB(textureId)
                ARBBindlessTexture.glMakeTextureHandleResidentARB(theHandle)
                unbind(15)
                theHandle
            }
        }
    }

    @JvmOverloads
    fun bind(setUsed: Boolean = true) {
        if (setUsed) {
            setUsedNow()
        }
        textureManager.gpuContext.bindTexture(target, textureId)
    }

    @JvmOverloads
    fun bind(unit: Int, setUsed: Boolean = true) {
        if (setUsed) {
            setUsedNow()
        }
        textureManager.gpuContext.bindTexture(unit, target, textureId)
    }

    fun unbind(unit: Int) {
        textureManager.gpuContext.bindTexture(unit, target, 0)
    }

    fun buffer(): ByteBuffer {
        val imageBuffer = ByteBuffer.allocateDirect(data!![0].size)
        imageBuffer.put(data[0], 0, data[0].size)
        imageBuffer.flip()
        return imageBuffer
    }

    fun upload(textureManager: TextureManager, srgba: Boolean) {
        if (data == null || data[0] == null) {
            return
        }
        upload(textureManager, buffer(), srgba)
    }

    @JvmOverloads
    fun upload(textureManager: TextureManager, textureBuffer: ByteBuffer, srgba: Boolean = false) {
        val doesntNeedUpload = UPLOADING == uploadState || UPLOADED == uploadState
        if (doesntNeedUpload) {
            return
        }

        uploadState = UPLOADING

        val uploadRunnable = {
            LOGGER.info("Uploading $path")
            var internalformat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
            if (srgba) {
                internalformat = EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT
            }
            val finalInternalformat = internalformat

            textureManager.gpuContext.execute {
                bind(15)
                if (target == TEXTURE_2D) {
                    GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR)
                    GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, magFilter)
                    GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT)
                    GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT)
                    GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0)
                    GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, Util.calculateMipMapCount(Math.max(width, height)))
                }
                unbind(15)
            }
            LOGGER.info("Actually uploading...")
            if (mipmapsGenerated) {
                LOGGER.info("Mipmaps already generated")
                uploadMipMaps(finalInternalformat)
            }
            uploadWithPixelBuffer(textureBuffer, finalInternalformat, width, height, 0, sourceDataCompressed, false)
            textureManager.gpuContext.execute {
                if (!mipmapsGenerated) {
                    bind(15)
                    GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
                    unbind(15)

                }
            }
            genHandle(textureManager)
            setUploaded()
            textureManager.postTextureChangedEvent()
        }

        textureManager.commandQueue.addCommand<Any>(uploadRunnable)
    }

    private fun setUploaded() {
        uploadState = UPLOADED
        LOGGER.info("Upload finished")
        LOGGER.fine("Free VRAM: " + textureManager.gpuContext.availableVRAM)
    }

    private fun uploadWithPixelBuffer(textureBuffer: ByteBuffer, internalformat: Int, width: Int, height: Int, mipLevel: Int, sourceDataCompressed: Boolean, setMaxLevel: Boolean) {
        textureBuffer.rewind()
        val pbo = AtomicInteger(-1)
        val temp = textureManager.gpuContext.calculate {
            bind(15)
            pbo.set(GL15.glGenBuffers())
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo.get())
            glBufferData(GL_PIXEL_UNPACK_BUFFER, textureBuffer.capacity().toLong(), GL_STREAM_COPY)
            val xxx = GL15.glMapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, GL15.GL_WRITE_ONLY, null)
            unbind(15)
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
            xxx
        }
        temp.put(textureBuffer)
        textureManager.gpuContext.execute {
            bind(15)
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo.get())
            GL15.glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER)

            if (sourceDataCompressed) {
                GL13.glCompressedTexImage2D(target.glTarget, mipLevel, internalformat, width, height, 0, textureBuffer.capacity(), 0)
            } else {
                GL11.glTexImage2D(target.glTarget, mipLevel, internalformat, width, height, 0, srcPixelFormat, GL11.GL_UNSIGNED_BYTE, 0)
            }
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
            GL15.glDeleteBuffers(pbo.get())
            val textureMaxLevel = mipmapCount - mipLevel
            if (setMaxLevel) {
                LOGGER.info("TextureMaxLevel: " + Math.max(0, textureMaxLevel))
                GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, textureMaxLevel)
            }
            unbind(15)

            if (mipmapsGenerated && mipLevel == 0) {
                setUploaded()
            }
        }
    }

    private fun uploadMipMaps(internalformat: Int) {
        LOGGER.info("Uploading mipmaps for $path")
        var currentWidth = width
        var currentHeight = height
        val widths = ArrayList<Int>()
        val heights = ArrayList<Int>()
        for (i in 0 until mipmapCount - 1) {
            val minSize = 1
            currentWidth = Math.max(minSize, currentWidth / 2)
            currentHeight = Math.max(minSize, currentHeight / 2)
            widths.add(currentWidth)
            heights.add(currentHeight)
        }
        for (mipmapIndex in mipmapCount - 1 downTo 1) {
            currentWidth = widths[mipmapIndex - 1]
            currentHeight = heights[mipmapIndex - 1]
            val tempBuffer = BufferUtils.createByteBuffer(data!![mipmapIndex].size)
            tempBuffer.rewind()
            tempBuffer.put(data[mipmapIndex])
            tempBuffer.rewind()
            LOGGER.info("Mipmap buffering with " + tempBuffer.remaining() + " remaining bytes for " + currentWidth + " x " + currentHeight)
            uploadWithPixelBuffer(tempBuffer, internalformat, currentWidth, currentHeight, mipmapIndex, sourceDataCompressed, true)
        }
    }

    fun getData(index: Int):ByteArray {
        while (uploadState != UPLOADED) {
        }
        return data[index]
    }
    override fun getData(): ByteArray {
        return getData(0)
    }

    override fun toString() = "(Texture)$path"

    override fun getName() = path

    override fun load() {
        if (UPLOADING == uploadState || UPLOADED == uploadState) {
            return
        }

        upload(textureManager, srgba)
    }

    override fun unload() {
        if (uploadState != UPLOADED || preventUnload) {
            return
        }

        LOGGER.info("Unloading $path")
        uploadState = NOT_UPLOADED

        textureManager.gpuContext.execute {
            ARBBindlessTexture.glMakeTextureHandleNonResidentARB(handle)
            LOGGER.info("Free VRAM: " + textureManager.gpuContext.availableVRAM)
        }
    }

    private fun bindWithoutReupload() {
        textureManager.gpuContext.bindTexture(target, textureId)
    }

    fun setPreventUnload(preventUnload: Boolean) {
        this.preventUnload = preventUnload
    }

    enum class UploadState {
        NOT_UPLOADED,
        UPLOADING,
        UPLOADED
    }

    companion object {
        private val LOGGER = Logger.getLogger(Texture::class.java.name)
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
            var bufferedImage = bufferedImage
            val start = System.currentTimeMillis()

            bufferedImage = rescaleToNextPowerOfTwo(bufferedImage)
            val ddsFile = File(getFullPathAsDDS(path))
            if (ddsFile.exists()) {
                ddsFile.delete()
            }
            DDSUtilWriteLock.lock()
            try {
                DDSUtil.write(ddsFile, bufferedImage, DDSImage.D3DFMT_DXT5, true)
            } finally {
                DDSUtilWriteLock.unlock()
            }
            LOGGER.info("Converting and saving as dds took " + (System.currentTimeMillis() - start) + "ms")
            return bufferedImage
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
            val imageBuffer = ByteBuffer.allocateDirect(data[0].size)
            imageBuffer.put(data[0], 0, data[0].size)
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
