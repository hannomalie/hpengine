package de.hanno.hpengine.graphics.texture

import InternalTextureFormat.*
import com.artemis.BaseSystem
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.config.NoUnloading
import de.hanno.hpengine.config.Unloading
import de.hanno.hpengine.directory.AbstractDirectory
import de.hanno.hpengine.graphics.Access
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.TextureTarget.TEXTURE_2D
import de.hanno.hpengine.graphics.constants.WrapMode
import de.hanno.hpengine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.texture.TextureDescription.Texture2DDescription
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.toCount
import kotlinx.coroutines.*
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.apache.logging.log4j.LogManager
import org.koin.core.annotation.Single
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import kotlin.math.max
import kotlin.math.pow


@Single(binds = [BaseSystem::class, TextureManager::class, TextureManagerBaseSystem::class, OpenGLTextureManager::class])
class OpenGLTextureManager(
    val config: Config,
    private val graphicsApi: OpenGLContext,
    programManager: OpenGlProgramManager,
) : TextureManagerBaseSystem() {
    private val logger = LogManager.getLogger(OpenGLTextureManager::class.java)
    init {
        logger.info("Creating system")
    }
    val engineDir = config.directories.engineDir

    override val textures: MutableMap<String, Texture> = ConcurrentHashMap()
    override val fileBasedTextures: MutableMap<String, FileBasedTexture2D> = ConcurrentHashMap()
    private val _texturePool: MutableList<DynamicFileBasedTexture2D> = CopyOnWriteArrayList()
    override val texturePool: List<DynamicFileBasedTexture2D> by ::_texturePool
    private val fallbacks: MutableList<TextureHandle<Texture2D>> = CopyOnWriteArrayList()
    private val allTextures: MutableList<Texture2D> = CopyOnWriteArrayList()

    override val texturesForDebugOutput: MutableMap<String, Texture> = LinkedHashMap()
    override val generatedCubeMaps = mutableMapOf<String, CubeMap>()

    init {
//    	loadAllAvailableTextures();
    }

    override fun registerGeneratedCubeMap(s: String, texture: CubeMap) {
        generatedCubeMaps[s] = texture
    }

    private val blur2dProgramSeparableHorizontal by lazy {
        programManager.getComputeProgram(
        programManager.config.directories.engineDir.resolve("shaders/${"blur2D_seperable_vertical_or_horizontal_compute.glsl"}")
            .toCodeSource(), Defines(Define("HORIZONTAL", true)), Uniforms.Empty
    )
    }
    private val blur2dProgramSeparableVertical by lazy {
        programManager.getComputeProgram(
        programManager.config.directories.engineDir.resolve("shaders/${"blur2D_seperable_vertical_or_horizontal_compute.glsl"}")
            .toCodeSource(), Defines(Define("VERTICAL", true)), Uniforms.Empty
    )
    }

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
            UploadState.Unloaded
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
                srgba -> COMPRESSED_SRGB_ALPHA_S3TC_DXT5
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

        val textureUnloadStrategy = config.performance.textureUnloadStrategy
        return if(unloadable && textureUnloadStrategy is Unloading) {
            val mipMapCountToKeepLoaded = textureUnloadStrategy.mipMapCountToKeepLoaded

            val highesMipMapDimensionToKeepLoaded = 2.0.pow(mipMapCountToKeepLoaded.toDouble() - 1).toInt()
            val highMipLevelDescription = textureDescription.copy(
                dimension = TextureDimension2D(highesMipMapDimensionToKeepLoaded, highesMipMapDimensionToKeepLoaded)
            )
            val highestMipLevelTexture = graphicsApi.Texture2D(
                highMipLevelDescription,
            )

            DynamicFileBasedTexture2D(
                resourcePath,
                file,
                null,
                null,
                textureDescription,
                UploadState.Unloaded
            ).apply {
                fileBasedTextures.ifAbsentPutInSingleThreadContext(resourcePath) { this }

                val fallbackHandle = StaticHandleImpl(
                    highestMipLevelTexture as Texture2D,
                    uploadState = UploadState.Unloaded,
                    currentMipMapBias = mipMapCountToKeepLoaded.toFloat(),
                ).apply {
                    fallbacks.add(this)
                }
                fallback = fallbackHandle
                GlobalScope.launch(Dispatchers.IO) {
                    graphicsApi.run {
                        val data = getData()
                        if(fallbackHandle.texture.textureFilterConfig.minFilter.isMipMapped) {
                            fallbackHandle.uploadWithoutPixelBuffer(data.subList(data.size - mipMapCountToKeepLoaded, data.size).mapIndexed { index, it ->
                                it.copy(mipMapLevel =  index)
                            })
                        } else {
                            fallbackHandle.uploadWithoutPixelBuffer(listOf(data.first()))
                        }
                    }
                    when(val fromPool = findSuitableHandleInPool(textureDescription)) {
                        null -> {
                            val texturesWithSameDescription = allTextures.count { it.description == textureDescription }
                            if(texturesWithSameDescription <= 10) {
                                texture = graphicsApi.Texture2D(textureDescription).apply {
                                    allTextures.add(this)
                                }
                            } else {
                                logger.info("Can't allocate $resourcePath, already $texturesWithSameDescription textures allocated of dimension ${textureDescription.dimension}")
                            }
                        }
                        else -> {
                            moveTexture(from = fromPool, to = this@apply)
                        }
                    }
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

    override fun getCubeMap(resourceName: String, file: File, srgba: Boolean): CubeMap {
        val tex: CubeMap = textures[resourceName + "_cube"] as CubeMap?
            ?: FileBasedOpenGLCubeMap(graphicsApi, resourceName, file, srgba).apply {
                loadAsync()
            }

        textures[resourceName + "_cube"] = tex
        return tex
    }

    fun getCubeMap(resourceName: String, files: List<File>, srgba: Boolean = true): CubeMap {
        val tex: CubeMap = textures[resourceName + "_cube"] as CubeMap?
            ?: FileBasedOpenGLCubeMap(graphicsApi, resourceName, files, srgba).apply {
                loadAsync()
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
        fallbacks.forEach { handle ->
            when(handle.uploadState) {
                is UploadState.MarkedForUpload, is UploadState.Unloaded -> {}
                UploadState.ForceFallback, UploadState.Uploaded, is UploadState.Uploading -> {
                    handle.currentMipMapBias -= world.delta * config.performance.mipBiasDecreasePerSecond
                }
            }
            if (handle.currentMipMapBias < 0f) {
                handle.currentMipMapBias = 0f
            }
        }
        val shouldBeLoadedButCouldNot = mutableListOf<DynamicFileBasedTexture2D>()
        fileBasedTextures.values.filterIsInstance<DynamicFileBasedTexture2D>().forEach { handle ->
            when(handle.uploadState) {
                is UploadState.MarkedForUpload, is UploadState.Unloaded -> {}
                UploadState.Uploaded, is UploadState.Uploading, UploadState.ForceFallback -> {
                    if(_texturePool.contains(handle)) {
//                        TODO: Smooth transition to fallback
//                        handle.handle.currentMipMapBias += world.delta * config.performance.mipBiasDecreasePerSecond
//                        handle as DynamicFileBasedTexture2D
//                        val maxMipMapLevelBeforeFallback = handle.texture!!.mipMapCount.toFloat() - (handle.fallback?.texture?.mipMapCount?.toFloat() ?: 0f)
//                        if (handle.handle.currentMipMapBias > maxMipMapLevelBeforeFallback) {
//                            handle.handle.currentMipMapBias = maxMipMapLevelBeforeFallback
//                        }
                    } else {
                        handle.handle.currentMipMapBias -= world.delta * config.performance.mipBiasDecreasePerSecond
                    }
                }
            }
            when(val uploadState = handle.uploadState) {
                UploadState.MarkedForUpload, UploadState.Unloaded, UploadState.Uploaded, UploadState.ForceFallback -> {}
                is UploadState.Uploading -> {
                    if(handle.handle.currentMipMapBias < uploadState.mipMapLevel + 1f) {
                        handle.handle.currentMipMapBias = uploadState.mipMapLevel + 1f
                    }
                }
            }
            if (handle.handle.currentMipMapBias < 0f) {
                handle.handle.currentMipMapBias = 0f
            }
            val canBeUnloaded = handle.canBeUnloaded
            val needsToBeLoaded = !canBeUnloaded

            when (handle.texture) {
                null -> if (needsToBeLoaded) {
                    when (val fromPool = findSuitableHandleInPool(handle.description)) {
                        null -> {
                            logger.trace("Cannot load ${handle.file.absolutePath}, no capacity left")
                            shouldBeLoadedButCouldNot.add(handle)
                        }

                        else -> {
                            moveTexture(from = fromPool, to = handle)
                            graphicsApi.run {
                                handle.uploadAsync()
                            }
                        }
                    }
                }

                else -> when (handle.uploadState) {
                    is UploadState.Unloaded -> if (needsToBeLoaded) {
                        logger.info("Uploading ${handle.path}")
                        graphicsApi.run {
                            handle.uploadAsync()
                        }
                    }

                    UploadState.Uploaded -> if (canBeUnloaded && !_texturePool.contains(handle)) {
                        when (config.performance.textureUnloadStrategy) {
                            NoUnloading -> {}
                            is Unloading -> {
                                logger.info("Unloading ${handle.file.absolutePath} - ${handle.description}")
                                returnToPool(handle)
                            }
                        }
                    }

                    is UploadState.Uploading, is UploadState.MarkedForUpload, UploadState.ForceFallback -> {}
                }
            }
            if(canBeUnloaded && graphicsApi.pixelBufferObjectPool.currentJobs.containsKey(handle)) {
                graphicsApi.pixelBufferObjectPool.currentJobs.remove(handle)?.cancel()
            }
        }

        val handles = fileBasedTextures.values.filterIsInstance<DynamicFileBasedTexture2D>().groupBy { it.texture != null }
        val withTexture = handles[true] ?: emptyList()
        val withoutTexture = (handles[false] ?: emptyList()).filter { !it.canBeUnloaded }.sortedBy { graphicsApi.getHandleUsageDistance(it) }

        withoutTexture.forEach { toLoad ->
            val toUnload = withTexture.firstOrNull { toUnload ->
                (graphicsApi.getHandleUsageDistance(toLoad) ?: Float.MAX_VALUE) < (graphicsApi.getHandleUsageDistance(toUnload) ?: Float.MAX_VALUE)
                        && toUnload.description == toLoad.description
                        && toUnload.texture != null
            }
            if(toUnload != null) {
                moveTexture(toUnload, toLoad)
                runBlocking { graphicsApi.pixelBufferObjectPool.currentJobs.remove(toUnload)?.cancelAndJoin() }
            }
        }
    }

    private fun moveTexture(
        from: DynamicFileBasedTexture2D,
        to: DynamicFileBasedTexture2D,
    ) {
        require(from != to) { "Cannot move move backing texture from and to the same texture: $from" }
        logger.info("Moving texture from ${from.path}(${from.currentMipMapBias}) to ${to.path}(${to.currentMipMapBias})")
        val textureFromPool = from.texture!!

        from.texture = null
        from.uploadState = UploadState.Unloaded
        from.currentMipMapBias =
            mipMapBiasForUploadState(UploadState.Unloaded, textureFromPool.description.dimension)


        to.uploadState = UploadState.Unloaded
        to.texture = textureFromPool
        to.currentMipMapBias = mipMapBiasForUploadState(UploadState.Unloaded, textureFromPool.description.dimension)
        logger.info("Moved texture from ${from.path}(${from.currentMipMapBias}) to ${to.path}(${to.currentMipMapBias})")
    }

    override val FileBasedTexture2D.canBeUnloaded: Boolean
        get() = when(val handle = handle) {
            is DynamicHandle -> when (val usageTimeStamp = graphicsApi.getHandleUsageTimeStamp(handle)) {
                null -> true
                else -> {
                    val notUsedForNanos = System.nanoTime() - usageTimeStamp
                    notUsedForNanos >= config.performance.unloadBiasInNanos
                }
            }
            is StaticHandle -> false
        }

    private fun returnToPool(texture: DynamicFileBasedTexture2D) {
        _texturePool.add(texture)
    }

    private fun findSuitableHandleInPool(textureDescription: Texture2DDescription) = _texturePool.firstOrNull {
        it.texture!!.description == textureDescription
    }?.apply {
        _texturePool.remove(this)
    }
}
