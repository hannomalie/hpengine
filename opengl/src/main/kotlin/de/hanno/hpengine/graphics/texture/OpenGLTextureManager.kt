package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import InternalTextureFormat.*
import com.artemis.BaseSystem
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.config.HighersMipMapToKeepLoaded
import de.hanno.hpengine.config.NoUnloading
import de.hanno.hpengine.config.UnloadCompletely
import de.hanno.hpengine.directory.AbstractDirectory
import de.hanno.hpengine.graphics.Access
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.constants.TextureTarget.*
import de.hanno.hpengine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.texture.TextureDescription.Texture2DDescription
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
import javax.imageio.ImageReader
import kotlin.math.max


@Single(binds = [BaseSystem::class, TextureManager::class, TextureManagerBaseSystem::class, OpenGLTextureManager::class])
class OpenGLTextureManager(
    val config: Config,
    private val graphicsApi: OpenGLContext,
    programManager: OpenGlProgramManager,
) : TextureManagerBaseSystem() {
    private val logger = LogManager.getLogger(OpenGLTextureManager::class.java)

    val engineDir = config.directories.engineDir

    override val textures: MutableMap<String, Texture> = ConcurrentHashMap()
    override val fileBasedTextures: MutableMap<String, FileBasedTexture2D> = ConcurrentHashMap()
    private val texturePool: MutableList<Pair<Texture2DDescription, Texture2D>> = CopyOnWriteArrayList()
    private val allTextures: MutableList<Texture2D> = CopyOnWriteArrayList()

    private var textureUsedAtTime: MutableMap<TextureHandle<*>, Long> = ConcurrentHashMap()
    override val texturesForDebugOutput: MutableMap<String, Texture> = LinkedHashMap()
    override val generatedCubeMaps = mutableMapOf<String, CubeMap>()

    init {
//    	loadAllAvailableTextures();
    }

    override val lensFlareTexture = getStaticTextureHandle("assets/textures/lens_flare_tex.jpg", true, engineDir).texture
    override fun registerGeneratedCubeMap(s: String, texture: CubeMap) {
        generatedCubeMaps[s] = texture
    }

    override var cubeMap = StaticHandleImpl(getCubeMap(
        "assets/textures/skybox/skybox.png",
        config.directories.engineDir.resolve("assets/textures/skybox/skybox.png")
    ), UploadState.Uploaded, 0f) // TODO: Verify if just setting this is okay

    //    var cubeMap = getCubeMap("assets/textures/skybox/skybox1.jpg", config.directories.engineDir.resolve("assets/textures/skybox/skybox5.jpg"))
    private val blur2dProgramSeparableHorizontal = programManager.getComputeProgram(
        programManager.config.directories.engineDir.resolve("shaders/${"blur2D_seperable_vertical_or_horizontal_compute.glsl"}")
            .toCodeSource(), Defines(Define("HORIZONTAL", true)), Uniforms.Empty
    )
    private val blur2dProgramSeparableVertical = programManager.getComputeProgram(
        programManager.config.directories.engineDir.resolve("shaders/${"blur2D_seperable_vertical_or_horizontal_compute.glsl"}")
            .toCodeSource(), Defines(Define("VERTICAL", true)), Uniforms.Empty
    )

    override val defaultTexture = getStaticTextureHandle("assets/textures/default/gi_flag.png", true, engineDir).apply {
        graphicsApi.run {
            uploadAsync()
        }
    }

    private fun loadAllAvailableTextures() {
        val textureDir = config.directories.engineDir.textures
        val files = FileUtils.listFiles(textureDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE).toList()
        for (file in files) {
            getTexture(file.absolutePath, srgba = false, directory = config.directories.engineDir, true)
        }
    }

    override fun getStaticTextureHandle(
        resourcePath: String,
        srgba: Boolean,
        directory: AbstractDirectory
    ): StaticFileBasedTexture2D {

        val file = directory.resolve(resourcePath)
        require(file.exists()) { "File ${file.absolutePath} must exist!" }
        require(file.isFile) { "File ${file.absolutePath} is not a file!" }
        val compressInternal = config.performance.textureCompressionByDefault

        val internalFormat = when {
            compressInternal -> when {
                srgba -> COMPRESSED_RGBA_S3TC_DXT5
                else -> COMPRESSED_RGBA_S3TC_DXT5
            }
            else -> when {
                srgba -> SRGB8_ALPHA8
                else -> RGBA16F
            }
        }

        val textureDescription = Texture2DDescription(
            getImageDimension(file),
            internalFormat = internalFormat,
            textureFilterConfig = TextureFilterConfig(),
            wrapMode = WrapMode.Repeat,
        )

        val openGLTexture = graphicsApi.Texture2D(
            textureDescription,
        ).apply {
            allTextures.add(this)
        }
        return StaticFileBasedTexture2D(
            resourcePath,
            file,
            openGLTexture,
            textureDescription,
            UploadState.Unloaded(null)
        ).apply {
            fileBasedTextures.ifAbsentPutInSingleThreadContext(resourcePath) { this }
        }
    }

    override fun getTexture(
        resourcePath: String,
        srgba: Boolean,
        directory: AbstractDirectory,
        unloadable: Boolean,
    ): FileBasedTexture2D {
        val file = directory.resolve(resourcePath)
        require(file.exists()) { "File ${file.absolutePath} must exist!" }
        require(file.isFile) { "File ${file.absolutePath} is not a file!" }
        val compressInternal = config.performance.textureCompressionByDefault

        val internalFormat = when {
            compressInternal -> when {
                srgba -> COMPRESSED_RGBA_S3TC_DXT5
                else -> COMPRESSED_RGBA_S3TC_DXT5
            }
            else -> when {
                srgba -> SRGB8_ALPHA8
                else -> RGBA16F
            }
        }
        val textureDescription = Texture2DDescription(
            getImageDimension(file),
            internalFormat = internalFormat,
            textureFilterConfig = TextureFilterConfig(),
            wrapMode = WrapMode.Repeat,
        )

        return if(unloadable) {
            val texturesWithSameDimension = allTextures.groupBy { it.dimension }[textureDescription.dimension]?.size ?: 0

            if(texturesWithSameDimension >= 100) {
                DynamicFileBasedTexture2D(
                    resourcePath,
                    file,
                    null,
                    textureDescription,
                    UploadState.Unloaded(null)
                ).apply {
                    fileBasedTextures.ifAbsentPutInSingleThreadContext(resourcePath) { this }

                    when(val fromPool = getFromPool(textureDescription)) {
                        null -> logger.info("Can't allocate $resourcePath, already $texturesWithSameDimension textures allocated of dimension ${textureDescription.dimension}")
                        else -> texture = fromPool
                    }
                }
            } else {
                val openGLTexture = getFromPool(textureDescription) ?: graphicsApi.Texture2D(
                    textureDescription,
                ).apply {
                    allTextures.add(this)
                }
                FileBasedTexture2D(
                    resourcePath,
                    file,
                    openGLTexture,
                    unloadable,
                    textureDescription,
                ).apply {
                    uploadState = UploadState.Unloaded(null)
                    fileBasedTextures.ifAbsentPutInSingleThreadContext(resourcePath) { this }
                }
            }
        } else {
            getStaticTextureHandle(
                resourcePath,
                srgba,
                directory
            )
        }
    }

    // https://stackoverflow.com/questions/1559253/java-imageio-getting-image-dimensions-without-reading-the-entire-file
    private fun getImageDimension(file: File): TextureDimension2D {
        ImageIO.createImageInputStream(file).use { inputStream ->
            val readers: Iterator<ImageReader> = ImageIO.getImageReaders(inputStream)
            if (readers.hasNext()) {
                val reader: ImageReader = readers.next()
                try {
                    reader.input = inputStream
                    return TextureDimension2D(reader.getWidth(0), reader.getHeight(0))
                } finally {
                    reader.dispose()
                }
            }
        }
        throw IllegalStateException("Can't determine dimension of ${file.absolutePath}")
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

    override fun setTexturesUsedInCycle(maps: MutableCollection<TextureHandle<*>>, cycle: Long) {
        maps.forEach { texture ->
            textureUsedAtTime[texture] = System.nanoTime()
        }
    }

    override fun getTextureUsedInCycle(texture: TextureHandle<*>) = textureUsedAtTime.getOrDefault(texture, 0)

    fun getCubeMap(resourceName: String, file: File, srgba: Boolean = true): CubeMap {
        val tex: CubeMap = textures[resourceName + "_cube"] as CubeMap?
            ?: FileBasedOpenGLCubeMap(graphicsApi, resourceName, file, srgba).apply {
                load()
            }

        textures[resourceName + "_cube"] = tex
        return tex
    }

    fun getCubeMap(resourceName: String, files: List<File>, srgba: Boolean = true): CubeMap {
        val tex: CubeMap = textures[resourceName + "_cube"] as CubeMap?
            ?: FileBasedOpenGLCubeMap(graphicsApi, resourceName, files, srgba).apply {
                load()
            }

        textures[resourceName + "_cube"] = tex
        return tex
    }

    override fun getTexture3D(
        description: TextureDescription.Texture3DDescription,
    ): OpenGLTexture3D {
        return OpenGLTexture3D.invoke(
            graphicsApi,
            description
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

    override fun processSystem() {
        val currentTime = System.nanoTime()

        fileBasedTextures.values.forEach { handle ->
            when(handle.uploadState) {
                is UploadState.MarkedForUpload -> {}
                is UploadState.Unloaded -> {}
                UploadState.Uploaded, is UploadState.Uploading -> {
                    handle.handle.currentMipMapBias -= world.delta * config.performance.mipBiasDecreasePerSecond
                }
            }
            if (handle.handle.currentMipMapBias < 0f) {
                handle.handle.currentMipMapBias = 0f
            }
            when(handle) {
                is DynamicFileBasedTexture2D -> {
                    val notUsedForNanos = currentTime - textureUsedAtTime.getOrDefault(handle, currentTime)
                    val canBeUnloaded = notUsedForNanos >= config.performance.unloadBiasInNanos
                    val needsToBeLoaded = !canBeUnloaded

                    when (val texture = handle.texture) {
                        null -> {
                            if(needsToBeLoaded) {
                                when(val fromPool = getFromPool(handle.description)) {
                                    null -> logger.debug("Cannot load ${handle.file.absolutePath}, no capacity left")
                                    else -> {
                                        logger.info("Now able to load texture formerly replaced by default texture: ${handle.path}")
                                        handle.texture = fromPool
                                        graphicsApi.run {
                                            handle.uploadAsync()
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            when (handle.uploadState) {
                                is UploadState.Unloaded -> {
                                    if (needsToBeLoaded) {
                                        logger.info("Uploading ${handle.path}")
                                        graphicsApi.run {
                                            handle.uploadAsync()
                                        }
                                    }
                                }

                                UploadState.Uploaded -> {
                                    if (canBeUnloaded) {//&& handle.currentMipMapBias == 0f) {
                                        when(val strategy = config.performance.textureUnloadStrategy) {
                                            is HighersMipMapToKeepLoaded -> {
                                                logger.info("Unloading ${handle.file.absolutePath}")
                                                val unloaded = UploadState.Unloaded(strategy.level)
                                                handle.uploadState = unloaded
                                                handle.currentMipMapBias = mipMapBiasForUploadState(unloaded, handle.description.dimension)
                                            }

                                            NoUnloading -> { }
                                            UnloadCompletely -> {
                                                logger.info("Unloading ${handle.file.absolutePath} - ${handle.description}")
                                                val unloaded = UploadState.Unloaded(null)
                                                handle.uploadState = unloaded
                                                handle.currentMipMapBias = mipMapBiasForUploadState(unloaded, handle.description.dimension)
                                                handle.texture = null
                                                returnToPool(handle.description, texture)
                                            }
                                        }
                                    }
                                }

                                is UploadState.Uploading -> {}
                                is UploadState.MarkedForUpload -> {}
                            }
                        }
                    }
                }
                is StaticFileBasedTexture2D -> {

                }
            }
        }
    }

    private fun returnToPool(
        texture2DDescription: Texture2DDescription,
        texture: Texture2D
    ) {
        texturePool.add(Pair(texture2DDescription, texture))
    }

    private fun getFromPool(textureDescription: Texture2DDescription) = texturePool.firstOrNull {
        it.first == textureDescription
    }?.apply {
        texturePool.remove(this)
    }?.second
}
