package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import InternalTextureFormat.*
import com.artemis.BaseSystem
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
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.toCount
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.apache.logging.log4j.LogManager
import org.koin.core.annotation.Single
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.max


@Single(binds = [BaseSystem::class, TextureManager::class, TextureManagerBaseSystem::class, OpenGLTextureManager::class])
class OpenGLTextureManager(
    val config: Config,
    private val graphicsApi: OpenGLContext,
    programManager: OpenGlProgramManager,
) : TextureManagerBaseSystem() {
    private val logger = LogManager.getLogger(OpenGLTextureManager::class.java)

    val engineDir = config.directories.engineDir

    /** The table of textures that have been loaded in this loader  */
    override var textures: MutableMap<String, Texture> = ConcurrentHashMap()

    private var textureUsedAtTime: MutableMap<Int, Long> = ConcurrentHashMap()
    override val texturesForDebugOutput: MutableMap<String, Texture> = LinkedHashMap()
    override val generatedCubeMaps = mutableMapOf<String, CubeMap>()

    init {
//    	loadAllAvailableTextures();
    }

    override val lensFlareTexture = getTexture("assets/textures/lens_flare_tex.jpg", true, engineDir)
    override fun registerGeneratedCubeMap(s: String, texture: CubeMap) {
        generatedCubeMaps[s] = texture
    }

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

    override val defaultTexture = getTexture("assets/textures/default/gi_flag.png", true, engineDir, true)

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
        directory: AbstractDirectory,
        unloadable: Boolean,
    ): FileBasedTexture2D<Texture2D> {
        val file = directory.resolve(resourcePath)
        require(file.exists()) { "File ${file.absolutePath} must exist!" }
        require(file.isFile) { "File ${file.absolutePath} is not a file!" }
        val compressInternal = config.performance.textureCompressionByDefault

        val internalFormat = if (compressInternal) {
            if (srgba) COMPRESSED_RGBA_S3TC_DXT5 else COMPRESSED_RGBA_S3TC_DXT5
        } else {
            if (srgba) SRGB8_ALPHA8 else RGBA16F
        }

        // TODO: Loading the whole image only to determine dimension is wasteful
        val bufferedImage = ImageIO.read(file) ?: throw IllegalStateException("Cannot load $file")

        val uploadInfo = graphicsApi.createUploadInfo(bufferedImage, internalFormat, srgba)
        // TODO: Abstract over indexable textures somehow
        val openGLTexture = graphicsApi.Texture2D(
            uploadInfo,
            WrapMode.Repeat
        )

        return FileBasedTexture2D(
            resourcePath,
            file,
            openGLTexture,
            unloadable,
        ).apply {
            uploadState = UploadState.Unloaded(mipmapCount - 1)
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

    override fun setTexturesUsedInCycle(maps: Collection<Texture>, cycle: Long) {
        maps.forEach { texture ->
            textureUsedAtTime[texture.id] = System.nanoTime()
        }
    }

    override fun getTextureUsedInCycle(texture: Texture) = textureUsedAtTime.getOrDefault(texture.id, 0)

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
                RGBA16F
            )
            blur2dProgramSeparableHorizontal.setUniform("width", finalWidth)
            blur2dProgramSeparableHorizontal.setUniform("height", finalHeight)
            blur2dProgramSeparableHorizontal.setUniform("mipmapSource", mipmapSource)
            blur2dProgramSeparableHorizontal.setUniform("mipmapTarget", mipmapTarget)
            blur2dProgramSeparableHorizontal.dispatchCompute(
                finalWidth.toCount() / 8,
                finalHeight.toCount() / 8,
                1.toCount()
            )

            blur2dProgramSeparableVertical.use()
            //            OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, sourceTexture);
            //            OpenGLContext.getInstance().bindImageTexture(1,sourceTexture, mipmapTarget, false, mipmapTarget, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
            blur2dProgramSeparableVertical.setUniform("width", finalWidth)
            blur2dProgramSeparableVertical.setUniform("height", finalHeight)
            blur2dProgramSeparableVertical.setUniform("mipmapSource", mipmapSource)
            blur2dProgramSeparableVertical.setUniform("mipmapTarget", mipmapTarget)
            blur2dProgramSeparableVertical.dispatchCompute(
                finalWidth.toCount() / 8,
                finalHeight.toCount() / 8,
                1.toCount()
            )
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
                RGBA16F
            )
            blur2dProgramSeparableHorizontal.setUniform("width", finalWidth)
            blur2dProgramSeparableHorizontal.setUniform("height", finalHeight)
            blur2dProgramSeparableHorizontal.setUniform("mipmapSource", mipmapSource)
            blur2dProgramSeparableHorizontal.setUniform("mipmapTarget", mipmapTarget)
            val num_groups_x = max(1, finalWidth / 8).toCount()
            val num_groups_y = max(1, finalHeight / 8).toCount()
            blur2dProgramSeparableHorizontal.dispatchCompute(num_groups_x, num_groups_y, 1.toCount())
        }
    }

    override fun registerTextureForDebugOutput(name: String, texture: Texture) {
        // TODO: Lift Texture2D to API
        if (texture.dimension is TextureDimension2D) {
            texturesForDebugOutput[name] = texture
        }
    }

    private val mipBiasDecreasePerSecond = 2f
    private val unloadBiasInNanos = TimeUnit.SECONDS.toNanos(2)
    override fun processSystem() {
        val currentTime = System.nanoTime()

        textures.values.filterIsInstance<Texture2D>().forEach { texture ->
            when(texture.uploadState) {
                is UploadState.MarkedForUpload -> {}
                is UploadState.Unloaded -> {}
                UploadState.Uploaded, is UploadState.Uploading -> {
                    texture.currentMipMapBias -= world.delta * mipBiasDecreasePerSecond
                }
            }
            if (texture.currentMipMapBias < 0f) {
                texture.currentMipMapBias = 0f
            }
        }

        // TODO: Support all textures
        textures.values.filterIsInstance<FileBasedTexture2D<*>>().forEach { texture ->
            val notUsedForNanos = currentTime - textureUsedAtTime.getOrDefault(texture.id, currentTime)
            val canBeUnloaded = notUsedForNanos >= unloadBiasInNanos
            val needsToBeLoaded = !canBeUnloaded

            when (texture.uploadState) {
                is UploadState.Unloaded -> {
                    if (needsToBeLoaded) {
                        if (texture is FileBasedTexture2D<*>) {
                            graphicsApi.run {
                                texture.uploadAsync() // TODO: Don't cast, use for all textures
                            }
                        }
                    }
                }
                UploadState.Uploaded -> {
                    if (texture.unloadable && canBeUnloaded && texture.currentMipMapBias == 0f) {
                        println("Unloading $texture")
                        val nextUploadState = UploadState.Unloaded(config.performance.maxMipMapToKeepLoaded)
                        texture.uploadState = nextUploadState
                        texture.currentMipMapBias = nextUploadState.mipMapLevel.toFloat()
                    }
                }
                is UploadState.Uploading -> {}
                is UploadState.MarkedForUpload -> {}
            }
        }
    }
}
