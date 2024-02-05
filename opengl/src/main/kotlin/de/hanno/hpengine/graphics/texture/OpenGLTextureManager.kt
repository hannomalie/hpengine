package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import com.artemis.BaseSystem
import ddsutil.DDSUtil
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.AbstractDirectory
import de.hanno.hpengine.graphics.Access
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.constants.TextureTarget.*
import de.hanno.hpengine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.texture.DDSConverter.availableAsDDS
import de.hanno.hpengine.graphics.texture.DDSConverter.getFullPathAsDDS
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.threads.TimeStepThread
import jogl.DDSImage
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.koin.core.annotation.Single
import java.awt.Color
import java.awt.image.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import javax.imageio.ImageIO


@Single(binds = [BaseSystem::class, TextureManager::class, TextureManagerBaseSystem::class, OpenGLTextureManager::class])
class OpenGLTextureManager(
    val config: Config,
    private val graphicsApi: OpenGLContext,
    programManager: OpenGlProgramManager,
) : TextureManagerBaseSystem() {

    val engineDir = config.directories.engineDir

    /** The table of textures that have been loaded in this loader  */
    override var textures: MutableMap<String, Texture> = LinkedHashMap()
    override val texturesForDebugOutput: MutableMap<String, Texture> = LinkedHashMap()

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

    override val lensFlareTexture = getTexture("assets/textures/lens_flare_tex.jpg", true, engineDir)
    override var cubeMap = getCubeMap(
        "assets/textures/skybox/skybox.png",
        config.directories.engineDir.resolve("assets/textures/skybox/skybox.png")
    )

    //    var cubeMap = getCubeMap("assets/textures/skybox/skybox1.jpg", config.directories.engineDir.resolve("assets/textures/skybox/skybox5.jpg"))
    private val blur2dProgramSeparableHorizontal = programManager.getComputeProgram(
        programManager.config.directories.engineDir.resolve("shaders/${"blur2D_seperable_vertical_or_horizontal_compute.glsl"}")
            .toCodeSource(), Defines(Define("HORIZONTAL", true)), Uniforms.Empty
    )
    private val blur2dProgramSeparableVertical = programManager.getComputeProgram(
        programManager.config.directories.engineDir.resolve("shaders/${"blur2D_seperable_vertical_or_horizontal_compute.glsl"}")
            .toCodeSource(), Defines(Define("VERTICAL", true)), Uniforms.Empty
    )

    private val temp = loadDefaultTexture()
    override val defaultTexture = temp.first

    private fun loadDefaultTexture(): Pair<FileBasedTexture2D<OpenGLTexture2D>, BufferedImage> {
        val defaultTexturePath = "assets/textures/default/gi_flag.png"
        val defaultTexture = getTexture(defaultTexturePath, true, engineDir)
        val defaultTextureAsBufferedImage = loadImage(defaultTexturePath)
        return Pair(defaultTexture, defaultTextureAsBufferedImage)
    }

    private fun loadAllAvailableTextures() {
        val textureDir = config.directories.engineDir.textures
        val files = FileUtils.listFiles(textureDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE).toList()
        for (file in files) {
            this.getTexture(file.absolutePath, srgba = false, directory = config.directories.engineDir)
        }
    }

    override fun getTexture(
        resourcePath: String,
        srgba: Boolean,
        directory: AbstractDirectory
    ) = getTexture(directory.resolve(resourcePath), resourcePath, srgba)

    fun getTexture(file: File, resourcePath: String = file.absolutePath, srgba: Boolean = false): FileBasedTexture2D<OpenGLTexture2D> {
        require(file.exists()) { "File ${file.absolutePath} must exist!" }
        require(file.isFile) { "File ${file.absolutePath} is not a file!" }

        val compressInternal = config.performance.textureCompressionByDefault
        val internalFormat = if (compressInternal) {
            if (srgba) InternalTextureFormat.COMPRESSED_RGBA_S3TC_DXT5 else InternalTextureFormat.COMPRESSED_RGBA_S3TC_DXT5
        } else {
            if (srgba) InternalTextureFormat.SRGB8_ALPHA8 else InternalTextureFormat.RGBA16F
        }

        val openGLTexture = if (file.extension == "dds") {
            val ddsImage = DDSImage.read(file)
            if (compressInternal) {
                val uploadInfo = UploadInfo.AllMipLevelsTexture2DUploadInfo(
                    TextureDimension(ddsImage.width, ddsImage.height),
                    ddsImage.allMipMaps.map { it.data },
                    dataCompressed = true,
                    srgba = srgba,
                    internalFormat = internalFormat,
                    textureFilterConfig = TextureFilterConfig(),
                )
                graphicsApi.Texture2D(
                    uploadInfo,
                    WrapMode.Repeat,
                )
            } else {
                val bufferedImage = DDSUtil.decompressTexture(
                    ddsImage.getMipMap(0).data,
                    ddsImage.width,
                    ddsImage.height,
                    ddsImage.compressionFormat
                ).apply {
                    DDSConverter.run { rescaleToNextPowerOfTwo() }
                }
                graphicsApi.Texture2D(
                    graphicsApi.createAllMipLevelsLazyTexture2DUploadInfo(bufferedImage, internalFormat),
                    wrapMode = WrapMode.Repeat,
                )
            }
        } else graphicsApi.Texture2D(
            graphicsApi.createAllMipLevelsLazyTexture2DUploadInfo(ImageIO.read(file), internalFormat),
            wrapMode = WrapMode.Repeat,
        )

        return FileBasedTexture2D(
            resourcePath,
            file,
            openGLTexture
        ).apply {
            textures.ifAbsentPutInSingleThreadContext(resourcePath) { this }
        }
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
            val mipMapCountPlusOne = calculateMipMapCount(ddsImage.width, ddsImage.height)
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
                    Format.RGB,
                    mipMapsGenerated,
                    sourceDataCompressed = true,
                    hasAlpha = false
                ), data.toTypedArray()
            )
        } else {
            val bufferedImage = loadImage(resourceName)

            val mipMapCountPlusOne = calculateMipMapCount(bufferedImage.width, bufferedImage.height)
            val mipMapCount = mipMapCountPlusOne - 1

            val hasAlpha = bufferedImage.colorModel.hasAlpha()
            val srcPixelFormat = if (hasAlpha) Format.RGBA else Format.RGB
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
            ?: FileBasedOpenGLCubeMap(graphicsApi, resourceName, file, srgba)

        textures[resourceName + "_cube"] = tex
        return tex
    }

    fun getCubeMap(resourceName: String, files: List<File>, srgba: Boolean = true): CubeMap {
        val tex: CubeMap = textures[resourceName + "_cube"] as CubeMap?
            ?: FileBasedOpenGLCubeMap(graphicsApi, resourceName, files, srgba)

        textures[resourceName + "_cube"] = tex
        return tex
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
            texImage = BufferedImage(alphaColorModel, raster, false, Hashtable<Any, Any>())
        } else {
            raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height, 3, null)
            texImage = BufferedImage(colorModel, raster, false, Hashtable<Any, Any>())
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

    fun copyCubeMap(sourceTexture: CubeMap): Int = graphicsApi.copyCubeMap(sourceTexture).id

    //TODO: Add texture filters as params
    fun getCubeMap(width: Int, height: Int, internalTextureFormat: InternalTextureFormat): Texture {
        return getTexture(width, height, internalTextureFormat, TEXTURE_CUBE_MAP)
    }

    fun getCubeMapArray(width: Int, height: Int, internalTextureFormat: InternalTextureFormat): Texture {
        return getTexture(width, height, internalTextureFormat, TEXTURE_CUBE_MAP_ARRAY, 1)
    }

    fun getCubeMapArray(width: Int, height: Int, internalTextureFormat: InternalTextureFormat, depth: Int): Texture {
        return getTexture(width, height, internalTextureFormat, TEXTURE_CUBE_MAP_ARRAY, depth)
    }

    fun getTexture(
        width: Int,
        height: Int,
        internalFormat: InternalTextureFormat,
        target: TextureTarget,
        depth: Int = 1
    ): Texture = when (target) {
        TEXTURE_2D -> graphicsApi.Texture2D(
            TextureDimension2D(width, height),
            target,
            internalFormat,
            TextureFilterConfig(),
            WrapMode.Repeat,
            UploadState.Uploaded
        )

        TEXTURE_CUBE_MAP -> graphicsApi.CubeMap(
            TextureDimension2D(width, height),
            internalFormat,
            TextureFilterConfig(),
            WrapMode.Repeat,
        )

        TEXTURE_CUBE_MAP_ARRAY -> TODO() // TODO: Implement those
        TEXTURE_2D_ARRAY -> TODO()
        TEXTURE_3D -> TODO()
    }

    override fun getTexture3D(
        gridResolution: Int,
        internalFormat: InternalTextureFormat,
        minFilter: MinFilter,
        magFilter: MagFilter,
        wrapMode: WrapMode
    ): Texture3D = OpenGLTexture3D(
        graphicsApi,
        TextureDimension(gridResolution, gridResolution, gridResolution),
        TextureFilterConfig(minFilter, magFilter),
        internalFormat,
        wrapMode
    )

    fun blur2DTextureRGBA16F(sourceTexture: Int, width: Int, height: Int, mipmapTarget: Int, mipmapSource: Int) {
        var width = width
        var height = height
        for (i in 0 until mipmapSource) {
            width /= 2
            height /= 2
        }
        val finalWidth = width
        val finalHeight = height
        graphicsApi.onGpu {
            blur2dProgramSeparableHorizontal.use()
            bindTexture(0, TEXTURE_2D, sourceTexture)
            bindImageTexture(
                1,
                sourceTexture,
                mipmapTarget,
                false,
                mipmapTarget,
                Access.WriteOnly,
                InternalTextureFormat.RGBA16F
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
        graphicsApi.onGpu {
            blur2dProgramSeparableHorizontal.use()
            bindTexture(0, TEXTURE_2D, sourceTexture)
            bindImageTexture(
                1,
                sourceTexture,
                mipmapTarget,
                false,
                mipmapTarget,
                Access.WriteOnly,
                InternalTextureFormat.RGBA16F
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

    companion object {
        private val LOGGER = Logger.getLogger(OpenGLTextureManager::class.java.name)
        private val TEXTURE_FACTORY_THREAD_COUNT = 1

        @Volatile
        @JvmField
        var TEXTURE_UNLOAD_THRESHOLD_IN_MS: Long = 10000
        private val USE_TEXTURE_STREAMING = false
    }

    override fun registerTextureForDebugOutput(name: String, texture: Texture) {
        // TODO: Lift Texture2D to API
        if (texture.dimension is TextureDimension2D) {
            texturesForDebugOutput[name] = texture
        }
    }

    override fun processSystem() {}

}
