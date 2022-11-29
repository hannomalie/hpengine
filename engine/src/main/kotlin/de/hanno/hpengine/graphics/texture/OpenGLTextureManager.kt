package de.hanno.hpengine.graphics.texture

import com.artemis.BaseSystem
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.AbstractDirectory
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.*
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget.*
import de.hanno.hpengine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.texture.DDSConverter.availableAsDDS
import de.hanno.hpengine.graphics.texture.DDSConverter.getFullPathAsDDS
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.threads.TimeStepThread
import de.hanno.hpengine.util.Util.calculateMipMapCountPlusOne
import jogl.DDSImage
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL13.GL_RGBA8
import org.lwjgl.opengl.GL30.GL_RGBA16F
import org.lwjgl.opengl.GL30.GL_RGBA32F
import java.awt.Color
import java.awt.image.*
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import javax.imageio.ImageIO


context(GpuContext)
class OpenGLTextureManager(
    val config: Config,
    programManager: OpenGlProgramManager,
) : BaseSystem(), TextureManager {

    val engineDir = config.directories.engineDir

    /** The table of textures that have been loaded in this loader  */
    var textures: MutableMap<String, Texture> = LinkedHashMap()
    val texturesForDebugOutput: MutableMap<String, Texture> = LinkedHashMap()

    init {
//    	loadAllAvailableTextures();

        if (USE_TEXTURE_STREAMING) {
            object : TimeStepThread("TextureWatcher", 0.5f) {
                override fun update(seconds: Float) {
                    val iterator = textures.values.iterator()
                    while (iterator.hasNext()) {
                        val texture = iterator.next()
                        val shouldUnload = false
                        if (shouldUnload) {
                            texture.unload()
                        }
                    }
                }
            }.start()
        }
    }

    override val lensFlareTexture = engineDir.getTexture("assets/textures/lens_flare_tex.jpg", true)
    override var cubeMap = getCubeMap(
        "assets/textures/skybox/skybox.png",
        config.directories.engineDir.resolve("assets/textures/skybox/skybox.png")
    )

    //    var cubeMap = getCubeMap("assets/textures/skybox/skybox1.jpg", config.directories.engineDir.resolve("assets/textures/skybox/skybox5.jpg"))
    private val blur2dProgramSeparableHorizontal = programManager.getComputeProgram(
        programManager.config.directories.engineDir.resolve("shaders/${"blur2D_seperable_vertical_or_horizontal_compute.glsl"}")
            .toCodeSource(), Defines(Define("HORIZONTAL", true))
    )
    private val blur2dProgramSeparableVertical = programManager.getComputeProgram(
        programManager.config.directories.engineDir.resolve("shaders/${"blur2D_seperable_vertical_or_horizontal_compute.glsl"}")
            .toCodeSource(), Defines(Define("VERTICAL", true))
    )

    private val temp = loadDefaultTexture()
    val defaultTexture = temp.first

    private fun loadDefaultTexture(): Pair<FileBasedTexture2D, BufferedImage> {
        val defaultTexturePath = "assets/textures/default/gi_flag.png"
        val defaultTexture = engineDir.getTexture(defaultTexturePath, true) as FileBasedTexture2D
        val defaultTextureAsBufferedImage = loadImage(defaultTexturePath)
        return Pair(defaultTexture, defaultTextureAsBufferedImage)
    }

    private fun loadAllAvailableTextures() {
        val textureDir = config.directories.engineDir.textures
        val files = FileUtils.listFiles(textureDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE) as List<File>
        for (file in files) {
            try {
                if (FilenameUtils.isExtension(file.absolutePath, "hptexture")) {
                    getTexture(file.absolutePath, directory = config.directories.gameDir)
                } else {
                    getCubeMap(file.absolutePath, config.directories.gameDir.resolve(file.absolutePath))
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }

    fun AbstractDirectory.getTexture(resourceName: String, srgba: Boolean = false) = getTexture(
        resourceName, srgba, this
    )

    fun getTexture(
        resourceName: String,
        srgba: Boolean = false,
        directory: AbstractDirectory = config.directories.gameDir
    ) = textures.ifAbsentPutInSingleThreadContext(resourceName) {
        FileBasedTexture2D(resourceName, directory, srgba)
    }

    fun getTexture(
        resourceName: String, srgba: Boolean = false, file: File
    ) = textures.ifAbsentPutInSingleThreadContext(resourceName) {
        FileBasedTexture2D(resourceName, file, srgba)
    }

    private inline fun <T> MutableMap<String, T>.ifAbsentPutInSingleThreadContext(
        resourceName: String,
        block: () -> T
    ): T {
        return if (!containsKey(resourceName)) {
            block().apply {
//                singleThreadContext.locked {
                put(resourceName, this@apply)
//                }
            }
        } else this[resourceName]!!
    }

    fun getCompleteTextureInfo(resourceName: String, srgba: Boolean): CompleteTextureInfo {
        val ddsImageAvailable = availableAsDDS(resourceName)
        LOGGER.fine("$resourceName available as dds: $ddsImageAvailable")
        return if (ddsImageAvailable) {
//        return if (resourceName.endsWith(".dds")) {
            val ddsImage = DDSImage.read(File(getFullPathAsDDS(resourceName)))
            val mipMapCountPlusOne = calculateMipMapCountPlusOne(ddsImage.width, ddsImage.height)
            val mipMapCount = mipMapCountPlusOne - 1

            val data = (0 until ddsImage.allMipMaps.size).map {
                val info = ddsImage.getMipMap(it)
//
//                val mipmapimage = DDSUtil.decompressTexture(info.data, info.width, info.height, info.compressionFormat)
//                data[i] = TextureManager.getInstance().convertImageData(mipmapImage)
//
                val array = ByteArray(info.data.capacity())
                info.data.get(array)
                CompletableFuture.completedFuture(array)
            }
            val mipMapsGenerated = ddsImage.numMipMaps > 1

            CompleteTextureInfo(
                TextureInfo(
                    srgba,
                    ddsImage.width,
                    ddsImage.height,
                    mipMapCount,
                    GL11.GL_RGB,
                    mipMapsGenerated,
                    sourceDataCompressed = true,
                    hasAlpha = false
                ), data.toTypedArray()
            )
        } else {
            val bufferedImage = loadImage(resourceName)

            val mipMapCountPlusOne = calculateMipMapCountPlusOne(bufferedImage.width, bufferedImage.height)
            val mipMapCount = mipMapCountPlusOne - 1

            val hasAlpha = bufferedImage.colorModel.hasAlpha()
            val srcPixelFormat = if (hasAlpha) GL11.GL_RGBA else GL11.GL_RGB
            val data = listOf(CompletableFuture.completedFuture(convertImageData(bufferedImage)))
            CompleteTextureInfo(
                TextureInfo(
                    srgba,
                    bufferedImage.width,
                    bufferedImage.height,
                    mipMapCount,
                    srcPixelFormat,
                    mipmapsGenerated = false,
                    sourceDataCompressed = false,
                    hasAlpha = hasAlpha
                ), data.toTypedArray()
            )
        }
    }

    fun getCubeMap(resourceName: String, file: File, srgba: Boolean = true): CubeMap {
        val tex: CubeMap = textures[resourceName + "_cube"] as CubeMap?
            ?: FileBasedOpenGLCubeMap(resourceName, file, srgba)

        textures[resourceName + "_cube"] = tex
        return tex
    }

    fun getCubeMap(resourceName: String, files: List<File>, srgba: Boolean = true): CubeMap {
        val tex: CubeMap = textures[resourceName + "_cube"] as CubeMap?
            ?: FileBasedOpenGLCubeMap(resourceName, files, srgba)

        textures[resourceName + "_cube"] = tex
        return tex
    }

    fun Texture.createTextureHandleAndMakeResident() = onGpu {
        handle = ARBBindlessTexture.glGetTextureHandleARB(id)
        ARBBindlessTexture.glMakeTextureHandleResidentARB(handle)
    }

    /**
     * Convert the buffered image to a de.hanno.de.hanno.hpengine.texture
     *
     * @param bufferedImage The image to convert to a de.hanno.de.hanno.hpengine.texture
     * @return A buffer containing the data
     */
    fun convertImageData(bufferedImage: BufferedImage): ByteArray {
        val raster: WritableRaster
        val texImage: BufferedImage

        val width = bufferedImage.width
        val height = bufferedImage.height

        if (bufferedImage.colorModel.hasAlpha()) {
            raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height, 4, null)
            texImage = BufferedImage(glAlphaColorModel, raster, false, Hashtable<Any, Any>())
        } else {
            raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height, 3, null)
            texImage = BufferedImage(glColorModel, raster, false, Hashtable<Any, Any>())
        }

        // copy the source image into the produced image
        val g = texImage.graphics
        g.color = Color(0f, 0f, 0f, 0f)
        g.fillRect(0, 0, width, height)
        g.drawImage(bufferedImage, 0, 0, null)

        return (texImage.raster.dataBuffer as DataBufferByte).data
    }

    /**
     * Load a given resource as a buffered image
     *
     * @param ref The location of the resource to load
     * @return The loaded buffered image
     * @throws IOException Indicates a failure to find a resource
     */
    fun loadImage(ref: String): BufferedImage {
        return loadImageAsStream(ref)
    }

    fun loadImageAsStream(ref: String): BufferedImage {
        val file = config.directories.engineDir.resolve(ref) // TODO: Inject dir
        return try {
            ImageIO.read(file)
        } catch (e: Exception) {
            System.err.println("Unable to read file $ref")
            throw e
        }

    }

    override fun generateMipMaps(textureTarget: TextureTarget, textureId: Int) {
        onGpu {
            bindTexture(textureTarget, textureId)
            GL30.glGenerateMipmap(textureTarget.glTarget)
        }
    }

    fun getTextureData(textureId: Int, mipLevel: Int, format: Int, pixels: ByteBuffer): ByteBuffer {
        bindTexture(TEXTURE_2D, textureId)
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, mipLevel, format, GL11.GL_UNSIGNED_BYTE, pixels)
        return pixels
    }

    fun copyCubeMap(sourceTextureId: Int, width: Int, height: Int, internalFormat: Int): Int {
        val copyTextureId = genTextures()
        bindTexture(15, TEXTURE_CUBE_MAP, copyTextureId)

        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR)
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE)
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)

        //		for(int i = 0; i < 6; i++) {
        //			GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, internalFormat, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
        //		}
        GL42.glTexStorage2D(TEXTURE_CUBE_MAP.glTarget, 1, internalFormat, width, height)

        GL43.glCopyImageSubData(
            sourceTextureId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
            copyTextureId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
            width, height, 6
        )

        bindTexture(15, TEXTURE_CUBE_MAP, 0)
        return copyTextureId
    }

    //TODO: Add texture filters as params
    fun getCubeMap(width: Int, height: Int, format: Int): Int {
        return getTexture(width, height, format, TEXTURE_CUBE_MAP)
    }

    fun getCubeMapArray(width: Int, height: Int, format: Int): Int {
        return getTexture(width, height, format, TEXTURE_CUBE_MAP_ARRAY, 1)
    }

    fun getCubeMapArray(width: Int, height: Int, format: Int, depth: Int): Int {
        return getTexture(width, height, format, TEXTURE_CUBE_MAP_ARRAY, depth)
    }

    fun getTexture(width: Int, height: Int, format: Int, target: TextureTarget, depth: Int = 1): Int {
        val textureId = genTextures()
        bindTexture(target, textureId)


        onGpu {
            setupTextureParameters(target)
            texStorage(target, format, width, height, depth, 1)
        }

        return textureId
    }

    fun texStorage(
        target: TextureTarget,
        internalFormat: Int,
        width: Int,
        height: Int,
        depth: Int,
        mipMapCount: Int
    ) = onGpu {
        when (target) {
            TEXTURE_CUBE_MAP_ARRAY -> GL42.glTexStorage3D(
                target.glTarget,
                mipMapCount,
                internalFormat,
                width,
                height,
                6 * depth
            )
            TEXTURE_3D -> GL42.glTexStorage3D(target.glTarget, mipMapCount, internalFormat, width, height, depth)
            else -> GL42.glTexStorage2D(target.glTarget, mipMapCount, internalFormat, width, height)
        }
    }

    fun texImage(target: TextureTarget, mipMapLevel: Int, internalFormat: Int, width: Int, height: Int, depth: Int) =
        onGpu {
            val format = GL11.GL_RGBA//if (internalFormat.hasAlpha) GL11.GL_RGBA else GL11.GL_RGB
            when {
                target == TEXTURE_CUBE_MAP_ARRAY -> throw NotImplementedError()
                target.is3D -> GL12.glTexImage3D(
                    target.glTarget,
                    mipMapLevel,
                    internalFormat,
                    width,
                    height,
                    depth,
                    mipMapLevel,
                    format,
                    GL11.GL_UNSIGNED_BYTE,
                    null as FloatBuffer?
                )
                else -> {
                    GL11.glTexImage2D(
                        target.glTarget,
                        mipMapLevel,
                        internalFormat,
                        width,
                        height,
                        0,
                        format,
                        GL11.GL_UNSIGNED_BYTE,
                        null as FloatBuffer?
                    )
                }
            }
        }

    //    TODO: The data buffer mustn't be null
    fun texSubImage(target: TextureTarget, internalFormat: Int, width: Int, height: Int, depth: Int) =
        onGpu {
            val format = GL11.GL_RGBA//if (internalFormat.hasAlpha) GL11.GL_RGBA else GL11.GL_RGB
            //null as FloatBuffer?)
            when (target) {
                TEXTURE_CUBE_MAP_ARRAY -> throw NotImplementedError()
                TEXTURE_3D -> GL12.glTexSubImage3D(
                    target.glTarget,
                    0,
                    0,
                    0,
                    0,
                    width,
                    height,
                    depth,
                    format,
                    GL11.GL_UNSIGNED_BYTE,
                    BufferUtils.createByteBuffer(width * height * depth * internalFormat.bytesPerTexel)
                )
                else -> GL11.glTexSubImage2D(
                    target.glTarget,
                    0,
                    0,
                    0,
                    width,
                    height,
                    format,
                    GL11.GL_UNSIGNED_BYTE,
                    BufferUtils.createByteBuffer(width * height * internalFormat.bytesPerTexel)
                )
            }
        }

    //    TODO: The data buffer mustn't be null
    fun compressedTexSubImage(target: TextureTarget, internalFormat: Int, width: Int, height: Int, depth: Int) =
        onGpu {
            val format = GL11.GL_RGBA//if (internalFormat.hasAlpha) GL11.GL_RGBA else GL11.GL_RGB
            //null as FloatBuffer?)
            when (target) {
                TEXTURE_CUBE_MAP_ARRAY -> throw NotImplementedError()
                TEXTURE_3D -> throw NotImplementedError()
                else -> GL13.glCompressedTexSubImage2D(
                    target.glTarget,
                    0,
                    0,
                    0,
                    width,
                    height,
                    format,
                    BufferUtils.createByteBuffer(width * height * internalFormat.bytesPerTexel)
                )
            }
        }

    // TODO: This should only work for internalFormats, not for all ints
    val Int.hasAlpha
        get() = intArrayOf(GL11.GL_RGBA8, GL30.GL_RGBA16F, GL30.GL_RGBA32F, GL30.GL_RGBA16I, GL30.GL_RGBA32I).contains(
            this
        )
    val Int.bytesPerTexel: Int
        // TODO: this is wroooooong but I couldn't figure out what size the formats are
        get() = when (this) {
            GL_RGBA8 -> 4
            GL_RGBA16F -> 8
            GL_RGBA32F -> 16
            EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT, EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT -> 8
            else -> throw NotImplementedError(" size for format $this not specified")
        }

    private fun setupTextureParameters(target: TextureTarget) {
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR)
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT)
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT)
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT)
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0)
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, 0)
        GL30.glGenerateMipmap(target.glTarget)
    }

    fun getTexture3D(
        gridResolution: Int,
        internalFormat: Int,
        minFilter: MinFilter,
        magFilter: MagFilter,
        wrapMode: Int
    ): OpenGLTexture3D {
        return OpenGLTexture3D(
            TextureDimension(gridResolution, gridResolution, gridResolution),
            TextureFilterConfig(minFilter, magFilter),
            internalFormat,
            wrapMode
        )
    }

    fun blur2DTextureRGBA16F(sourceTexture: Int, width: Int, height: Int, mipmapTarget: Int, mipmapSource: Int) {
        var width = width
        var height = height
        for (i in 0 until mipmapSource) {
            width /= 2
            height /= 2
        }
        val finalWidth = width
        val finalHeight = height
        onGpu {
            blur2dProgramSeparableHorizontal.use()
            bindTexture(0, TEXTURE_2D, sourceTexture)
            bindImageTexture(
                1,
                sourceTexture,
                mipmapTarget,
                false,
                mipmapTarget,
                GL15.GL_WRITE_ONLY,
                GL30.GL_RGBA16F
            )
            blur2dProgramSeparableHorizontal.setUniform("width", finalWidth)
            blur2dProgramSeparableHorizontal.setUniform("height", finalHeight)
            blur2dProgramSeparableHorizontal.setUniform("mipmapSource", mipmapSource)
            blur2dProgramSeparableHorizontal.setUniform("mipmapTarget", mipmapTarget)
            blur2dProgramSeparableHorizontal.dispatchCompute(finalWidth / 8, finalHeight / 8, 1)

            blur2dProgramSeparableVertical.use()
            //            OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, sourceTexture);
            //            OpenGLContext.getInstance().bindImageTexture(1,sourceTexture, mipmapTarget, false, mipmapTarget, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
            blur2dProgramSeparableVertical.setUniform("width", finalWidth)
            blur2dProgramSeparableVertical.setUniform("height", finalHeight)
            blur2dProgramSeparableVertical.setUniform("mipmapSource", mipmapSource)
            blur2dProgramSeparableVertical.setUniform("mipmapTarget", mipmapTarget)
            blur2dProgramSeparableVertical.dispatchCompute(finalWidth / 8, finalHeight / 8, 1)
        }
    }

    fun blurHorinzontal2DTextureRGBA16F(
        sourceTexture: Int,
        width: Int,
        height: Int,
        mipmapTarget: Int,
        mipmapSource: Int
    ) {
        var width = width
        var height = height
        for (i in 0 until mipmapSource) {
            width /= 2
            height /= 2
        }
        val finalWidth = width
        val finalHeight = height
        onGpu {
            blur2dProgramSeparableHorizontal.use()
            bindTexture(0, TEXTURE_2D, sourceTexture)
            bindImageTexture(
                1,
                sourceTexture,
                mipmapTarget,
                false,
                mipmapTarget,
                GL15.GL_WRITE_ONLY,
                GL30.GL_RGBA16F
            )
            blur2dProgramSeparableHorizontal.setUniform("width", finalWidth)
            blur2dProgramSeparableHorizontal.setUniform("height", finalHeight)
            blur2dProgramSeparableHorizontal.setUniform("mipmapSource", mipmapSource)
            blur2dProgramSeparableHorizontal.setUniform("mipmapTarget", mipmapTarget)
            val num_groups_x = Math.max(1, finalWidth / 8)
            val num_groups_y = Math.max(1, finalHeight / 8)
            blur2dProgramSeparableHorizontal.dispatchCompute(num_groups_x, num_groups_y, 1)
        }
    }

    fun Texture.delete() = onGpu {
        deleteTexture(id)
    }

    companion object {
        private val LOGGER = Logger.getLogger(OpenGLTextureManager::class.java.name)
        private val TEXTURE_FACTORY_THREAD_COUNT = 1

        @Volatile
        @JvmField
        var TEXTURE_UNLOAD_THRESHOLD_IN_MS: Long = 10000
        private val USE_TEXTURE_STREAMING = false

        fun deleteTexture(id: Int) {
            GL11.glDeleteTextures(id)
        }
    }

    override fun registerTextureForDebugOutput(name: String, texture: Texture) {
        // TODO: Lift Texture2D to API
        if (texture.dimension is TextureDimension2D) {
            texturesForDebugOutput[name] = texture
        }
    }

    override fun processSystem() {}

}
