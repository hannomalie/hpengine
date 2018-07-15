package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.TexturesChangedEvent
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.*
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.define.Define.getDefine
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.texture.OpenGlTexture.Companion.autoConvertToDDS
import de.hanno.hpengine.engine.model.texture.OpenGlTexture.Companion.buffer
import de.hanno.hpengine.engine.model.texture.OpenGlTexture.Companion.getFullPathAsDDS
import de.hanno.hpengine.engine.model.texture.OpenGlTexture.Companion.textureAvailableAsDDS
import de.hanno.hpengine.engine.threads.TimeStepThread
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.commandqueue.CommandQueue
import de.hanno.hpengine.util.commandqueue.FutureCallable
import jogl.DDSImage
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.joml.Vector2f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL13.*
import java.awt.Color
import java.awt.color.ColorSpace
import java.awt.image.*
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import javax.imageio.ImageIO

class TextureManager(private val eventBus: EventBus, programManager: ProgramManager,
                     val gpuContext: GpuContext) : Manager {

    val commandQueue = CommandQueue()

    /** The colour model including alpha for the GL image  */
    private val glAlphaColorModel = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
            intArrayOf(8, 8, 8, 8),
            true,
            false,
            ComponentColorModel.TRANSLUCENT,
            DataBuffer.TYPE_BYTE)

    /** The colour model for the GL image  */
    private val glColorModel = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
            intArrayOf(8, 8, 8, 0),
            false,
            false,
            ComponentColorModel.OPAQUE,
            DataBuffer.TYPE_BYTE)

    init {
//    	loadAllAvailableTextures();

        if (USE_TEXTURE_STREAMING) {
            object : TimeStepThread("TextureWatcher", 0.5f) {
                override fun update(seconds: Float) {
                    val iterator = textures.values.iterator()
                    while (iterator.hasNext()) {
                        val texture = iterator.next()
                        val notUsedSinceMs = System.currentTimeMillis() - texture.lastUsedTimeStamp
                        if (notUsedSinceMs in (TEXTURE_UNLOAD_THRESHOLD_IN_MS + 1)..19999) {
                            texture.unload()
                        }
                    }
                }
            }.start()
        }

        for (i in 0 until 1 + TEXTURE_FACTORY_THREAD_COUNT) {
            object : TimeStepThread("TextureManager$i", 0.01f) {
                override fun update(seconds: Float) {
                    commandQueue.executeCommands()
                }
            }.start()
        }
    }

    /** The table of textures that have been loaded in this loader  */
    var textures: MutableMap<String, Texture> = ConcurrentHashMap()

    val lensFlareTexture = getTexture("hp/assets/textures/lens_flare_tex.jpg", true)
    var cubeMap = getCubeMap("hp/assets/textures/skybox.png")
    private val blur2dProgramSeparableHorizontal = programManager.getComputeProgram("blur2D_seperable_vertical_or_horizontal_compute.glsl", Defines(getDefine("HORIZONTAL", true)))
    private val blur2dProgramSeparableVertical = programManager.getComputeProgram("blur2D_seperable_vertical_or_horizontal_compute.glsl", Defines(getDefine("VERTICAL", true)))

    private val temp = loadDefaultTexture()
    val defaultTexture = temp.first
    val defaultTextureAsBufferedImage = temp.second


    private fun loadDefaultTexture(): Pair<Texture, BufferedImage> {
        val defaultTexturePath = "hp/assets/models/textures/gi_flag.png"
        val defaultTexture = getTexture(defaultTexturePath, true)
        val defaultTextureAsBufferedImage = loadImage(defaultTexturePath) ?: throw IllegalStateException("Cannot load default texture!")
        return Pair(defaultTexture, defaultTextureAsBufferedImage)
    }

    private fun loadAllAvailableTextures() {
        val textureDir = File(OpenGlTexture.directory)
        val files = FileUtils.listFiles(textureDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE) as List<File>
        GpuContext.exitOnGLError("Before loadAllAvailableTextures")
        for (file in files) {
            try {
                if (FilenameUtils.isExtension(file.absolutePath, "hptexture")) {
                    getTexture(file.absolutePath)
                } else {
                    getCubeMap(file.absolutePath)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }

    fun removeTexture(path: String): Boolean {
        if (textures.containsKey(path)) {
            textures.remove(path)
            return !textures.containsKey(path)
        }
        return true
    }

    @JvmOverloads
    fun getTexture(resourceName: String, srgba: Boolean = false): Texture {
        if (textureLoaded(resourceName)) {
            return textures[resourceName]!!
        }
        LOGGER.info("$resourceName requested")

        return convertAndUpload(resourceName, srgba) ?: {
            LOGGER.severe("No texture loaded for $resourceName")
            defaultTexture
        }()
    }

    private fun convertAndUpload(resourceName: String, srgba: Boolean): Texture? {
        return commandQueue.calculate(object : FutureCallable<OpenGlTexture>() {
            override fun execute(): OpenGlTexture? {
                return getOpenGlTexture(resourceName, srgba)
            }
        })?.apply {
            textures[resourceName] = this
        }
    }

    private fun getOpenGlTexture(resourceName: String, srgba: Boolean): OpenGlTexture? {
        return try {
            val start = System.currentTimeMillis()
            val imageExists = File(resourceName).exists()
            if (imageExists) {
                val textureInfo = getCompleteTextureInfo(resourceName, srgba)

                OpenGlTexture(this@TextureManager,
                        resourceName,
                        srgba,
                        textureInfo.info.width,
                        textureInfo.info.height,
                        textureInfo.info.mipMapCount,
                        textureInfo.info.srcPixelFormat,
                        gpuContext.genTextures(), GL11.GL_LINEAR_MIPMAP_LINEAR, GL11.GL_LINEAR).apply {

                    upload(textureInfo)
                }
            } else {
                defaultTexture as OpenGlTexture // TODO: Remove this cast
            }.apply {
                LOGGER.info("" + (System.currentTimeMillis() - start) + "ms for loading and uploading as dds with mipmaps: " + resourceName)
                postTextureChangedEvent()

            }
        } catch (e: IOException) {
            e.printStackTrace()
            LOGGER.severe("Texture not found: $resourceName. Default texture returned...")
            return null
        } catch (e: NullPointerException) {
            e.printStackTrace()
            LOGGER.severe("Texture not found: $resourceName. Default texture returned...")
            return null
        }

    }

    fun getCompleteTextureInfo(resourceName: String, srgba: Boolean): CompleteTextureInfo {
        val ddsImageAvailable = textureAvailableAsDDS(resourceName)
        LOGGER.info("$resourceName available as dds: $ddsImageAvailable")
        return if (ddsImageAvailable) {
            val ddsImage = DDSImage.read(File(getFullPathAsDDS(resourceName)))
            val mipMapCountPlusOne = Util.calculateMipMapCountPlusOne(ddsImage.width, ddsImage.height)
            val mipMapCount = mipMapCountPlusOne - 1

            val data = (0 until ddsImage.allMipMaps.size).map {
                val info = ddsImage.getMipMap(it)
    //                        BufferedImage mipmapimage = DDSUtil.decompressTexture(info.getData(), info.getWidth(), info.getHeight(), info.getCompressionFormat());
    //                        showAsTextureInFrame(mipmapImage);
    //                        data[i] = TextureManager.getInstance().convertImageData(mipmapImage);
                val array = ByteArray(info.data.capacity())
                info.data.get(array)
                CompletableFuture<ByteArray>().apply { complete(array) }
            }
            val mipMapsGenerated = ddsImage.numMipMaps > 1

            CompleteTextureInfo(TextureInfo(srgba, ddsImage.width, ddsImage.height, mipMapCount, GL11.GL_RGB, mipMapsGenerated, true), data.toTypedArray())
        } else {
            val bufferedImage = (loadImage(resourceName) ?: throw IllegalStateException("Can not load texture $resourceName")).apply {
                    handleDdsConversion(resourceName, this)
                }

            val mipMapCountPlusOne = Util.calculateMipMapCountPlusOne(bufferedImage.width, bufferedImage.height)
            val mipMapCount = mipMapCountPlusOne - 1

            val srcPixelFormat = if (bufferedImage.colorModel.hasAlpha()) GL11.GL_RGBA else GL11.GL_RGB
            val data = listOf(CompletableFuture<ByteArray>().apply { convertImageData(bufferedImage) })
            CompleteTextureInfo(TextureInfo(srgba, bufferedImage.width, bufferedImage.height, mipMapCount, srcPixelFormat, false, false), data.toTypedArray())
        }
    }

    private fun handleDdsConversion(resourceName: String, image: BufferedImage) {
        if (autoConvertToDDS) {
            Thread {
                try {
                    OpenGlTexture.saveAsDDS(resourceName, image)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }.start()
        }
    }

    private fun textureLoaded(resourceName: String): Boolean {
        return textures.containsKey(resourceName)
    }

    private fun cubeMapPreCompiled(resourceName: String): Boolean {
        val fileName = FilenameUtils.getBaseName(resourceName)
        val f = File(OpenGlTexture.directory + fileName + ".hpcubemap")
        return f.exists()
    }

    @Throws(IOException::class)
    fun getCubeMap(resourceName: String): CubeMap? {
        val tex: CubeMap? = textures[resourceName + "_cube"] as CubeMap? ?: getCubeMap(resourceName,
                GL11.GL_RGBA,
                GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR)

        textures[resourceName + "_cube"] = tex as Texture
        return tex
    }

    @Throws(IOException::class)
    private fun getCubeMap(resourceName: String,
                           dstPixelFormat: Int,
                           minFilter: Int,
                           magFilter: Int): CubeMap {


        val bufferedImage: BufferedImage = loadImage(resourceName) ?: throw IOException("Cannot load image $resourceName")

        val srcPixelFormat = if (bufferedImage.colorModel.hasAlpha()) {
            GL11.GL_RGBA
        } else {
            GL11.GL_RGB
        }
        val width = bufferedImage.width
        val height = bufferedImage.height

        val data = convertCubeMapData(bufferedImage, width, height, glAlphaColorModel, glColorModel)

        return CubeMap(this, resourceName, width, height, minFilter, magFilter, srcPixelFormat, dstPixelFormat, gpuContext.genTextures(), data).apply {
            upload(this)
        }
    }

    fun upload(cubeMap: CubeMap) {

        gpuContext.execute {
            gpuContext.bindTexture(cubeMap)
            run {
                GL11.glTexParameteri(cubeMap.target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, cubeMap.minFilter)
                GL11.glTexParameteri(cubeMap.target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, cubeMap.magFilter)
                GL11.glTexParameteri(cubeMap.target.glTarget, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexParameteri(cubeMap.target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexParameteri(cubeMap.target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexParameteri(cubeMap.target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0)
                GL11.glTexParameteri(cubeMap.target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, de.hanno.hpengine.util.Util.calculateMipMapCount(Math.max(cubeMap.width, cubeMap.height)))
            }


            val perFaceBuffer = ByteBuffer.allocateDirect(cubeMap.getData()[0].size)

            cubeMap.load(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X, CubeMap.buffer(perFaceBuffer, cubeMap.getData()[1])) //1
            cubeMap.load(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X, CubeMap.buffer(perFaceBuffer, cubeMap.getData()[0])) //0
            cubeMap.load(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, CubeMap.buffer(perFaceBuffer, cubeMap.getData()[2]))
            cubeMap.load(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y, CubeMap.buffer(perFaceBuffer, cubeMap.getData()[3]))
            cubeMap.load(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, CubeMap.buffer(perFaceBuffer, cubeMap.getData()[4]))
            cubeMap.load(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z, CubeMap.buffer(perFaceBuffer, cubeMap.getData()[5]))

            this@TextureManager.generateMipMapsCubeMap(cubeMap.textureId)
            cubeMap.handle = ARBBindlessTexture.glGetTextureHandleARB(cubeMap.textureId)
            ARBBindlessTexture.glMakeTextureHandleResidentARB(cubeMap.handle)
        }
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
    @Throws(IOException::class)
    fun loadImage(ref: String): BufferedImage? {
        val url = TextureManager::class.java.classLoader.getResource(ref) ?: return loadImageAsStream(ref)

        val file = File(ref)
        return ImageIO.read(file)
    }

    @Throws(IOException::class)
    fun loadImageAsStream(ref: String): BufferedImage? {
        val file = File(ref)
        return try {
            ImageIO.read(file)
        } catch (e: Exception) {
            System.err.println("Unable to read file $ref")
            throw e
        }

    }

    private fun generateMipMaps(texture: OpenGlTexture, mipmap: Boolean) {
        gpuContext.execute {
            gpuContext.bindTexture(15, texture)
            if (mipmap) {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
            }

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT)
            gpuContext.unbindTexture(15, texture)
        }
    }

    @JvmOverloads
    fun generateMipMaps(textureId: Int, textureMinFilter: Int = GL11.GL_LINEAR_MIPMAP_LINEAR, textureMagFilter: Int = GL11.GL_LINEAR) {
        gpuContext.activeTexture(GL_TEXTURE0)
        gpuContext.bindTexture(TEXTURE_2D, textureId)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, textureMagFilter)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureMinFilter)
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
    }

    fun enableMipMaps(textureId: Int, textureMinFilter: Int, textureMagFilter: Int) {
        gpuContext.bindTexture(TEXTURE_2D, textureId)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, textureMagFilter)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureMinFilter)
    }

    fun generateMipMapsCubeMap(textureId: Int) {
        gpuContext.execute {
            gpuContext.activeTexture(GL_TEXTURE0)
            gpuContext.bindTexture(TEXTURE_CUBE_MAP, textureId)
            GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP)
        }
    }

    fun getTextureData(textureId: Int, mipLevel: Int, format: Int, pixels: ByteBuffer): ByteBuffer {
        gpuContext.bindTexture(TEXTURE_2D, textureId)
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, mipLevel, format, GL11.GL_UNSIGNED_BYTE, pixels)
        return pixels
    }

    fun copyCubeMap(sourceTextureId: Int, width: Int, height: Int, internalFormat: Int): Int {
        val copyTextureId = gpuContext.genTextures()
        gpuContext.bindTexture(15, TEXTURE_CUBE_MAP, copyTextureId)

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

        GL43.glCopyImageSubData(sourceTextureId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
                copyTextureId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
                width, height, 6)

        gpuContext.bindTexture(15, TEXTURE_CUBE_MAP, 0)
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

    @JvmOverloads
    fun getTexture(width: Int, height: Int, format: Int, target: GlTextureTarget, depth: Int = 1): Int {
        val textureId = gpuContext.genTextures()
        gpuContext.bindTexture(target, textureId)


        gpuContext.execute {
            setupTextureParameters(target)
            if (target == TEXTURE_CUBE_MAP_ARRAY) {
                GL42.glTexStorage3D(target.glTarget, 1, format, width, height, 6 * depth)
            } else {
                GL42.glTexStorage2D(target.glTarget, 1, format, width, height)
            }
        }

        return textureId
    }

    private fun setupTextureParameters(target: GlTextureTarget) {
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR)
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT)
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT)
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT)
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0)
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, 0)
        GL30.glGenerateMipmap(target.glTarget)
    }

    // TODO return proper object
    fun getTexture3D(gridSize: Int, gridTextureFormatSized: Int, filterMin: Int, filterMag: Int, wrapMode: Int): Int {
        val grid = IntArray(1)
        gpuContext.execute {
            grid[0] = GL11.glGenTextures()
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, grid[0])
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, filterMin)
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, filterMag)
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, wrapMode)
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, wrapMode)
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, wrapMode)
            GL42.glTexStorage3D(GL12.GL_TEXTURE_3D, de.hanno.hpengine.util.Util.calculateMipMapCount(gridSize), gridTextureFormatSized, gridSize, gridSize, gridSize)
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, grid[0])
            GL30.glGenerateMipmap(GL12.GL_TEXTURE_3D)
        }
        return grid[0]
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
        gpuContext.execute {
            blur2dProgramSeparableHorizontal.use()
            gpuContext.bindTexture(0, TEXTURE_2D, sourceTexture)
            gpuContext.bindImageTexture(1, sourceTexture, mipmapTarget, false, mipmapTarget, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F)
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

    fun blurHorinzontal2DTextureRGBA16F(sourceTexture: Int, width: Int, height: Int, mipmapTarget: Int, mipmapSource: Int) {
        var width = width
        var height = height
        for (i in 0 until mipmapSource) {
            width /= 2
            height /= 2
        }
        val finalWidth = width
        val finalHeight = height
        gpuContext.execute {
            blur2dProgramSeparableHorizontal.use()
            gpuContext.bindTexture(0, TEXTURE_2D, sourceTexture)
            gpuContext.bindImageTexture(1, sourceTexture, mipmapTarget, false, mipmapTarget, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F)
            blur2dProgramSeparableHorizontal.setUniform("width", finalWidth)
            blur2dProgramSeparableHorizontal.setUniform("height", finalHeight)
            blur2dProgramSeparableHorizontal.setUniform("mipmapSource", mipmapSource)
            blur2dProgramSeparableHorizontal.setUniform("mipmapTarget", mipmapTarget)
            val num_groups_x = Math.max(1, finalWidth / 8)
            val num_groups_y = Math.max(1, finalHeight / 8)
            blur2dProgramSeparableHorizontal.dispatchCompute(num_groups_x, num_groups_y, 1)
        }
    }

    @JvmOverloads
    fun OpenGlTexture.upload(textureInfo: CompleteTextureInfo) {
        val doesNotNeedUpload = OpenGlTexture.UploadState.UPLOADING == uploadState || OpenGlTexture.UploadState.UPLOADED == uploadState
        if (doesNotNeedUpload) {
            return
        }

        uploadState = OpenGlTexture.UploadState.UPLOADING

        val uploadRunnable = {
            LOGGER.info("Uploading $path")
            val internalFormat = if (srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT

            gpuContext.execute {
                gpuContext.bindTexture(15, this)
                GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR)
                GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, magFilter)
                GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT)
                GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT)
                GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0)
                GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, textureInfo.info.mipMapCount)
                gpuContext.unbindTexture(15, this)
            }
            if (textureInfo.info.mipmapsGenerated) {
                LOGGER.info("Mipmaps already generated")
                uploadMipMaps(textureInfo, internalFormat)
            }
            uploadWithPixelBuffer(gpuContext, textureInfo, buffer(textureInfo.data), internalFormat, width, height, 0, textureInfo.info.sourceDataCompressed, false)
            gpuContext.execute {
                if (!textureInfo.info.mipmapsGenerated) {
                    gpuContext.bindTexture(15, this)
                    GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
                    gpuContext.unbindTexture(15, this)
                }
            }
            handle = Texture.genHandle(this@TextureManager, textureId)
            uploadState = OpenGlTexture.UploadState.UPLOADED
            this@TextureManager.postTextureChangedEvent()
        }

        this@TextureManager.commandQueue.addCommand<Any>(uploadRunnable)
    }

    private fun OpenGlTexture.uploadWithPixelBuffer(gpuContext: GpuContext, textureInfo: CompleteTextureInfo, textureBuffer: ByteBuffer, internalFormat: Int, width: Int, height: Int, mipLevel: Int, sourceDataCompressed: Boolean, setMaxLevel: Boolean) {
        textureBuffer.rewind()
        val pbo = AtomicInteger(-1)
        val pixelUnpackBuffer = gpuContext.calculate {
            gpuContext.bindTexture(15, this)
            pbo.set(GL15.glGenBuffers())
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo.get())
            GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, textureBuffer.capacity().toLong(), GL15.GL_STREAM_COPY)
            val theBuffer = GL15.glMapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, GL15.GL_WRITE_ONLY, null)
            gpuContext.unbindTexture(15, this)
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
            theBuffer
        }
        pixelUnpackBuffer.put(textureBuffer)
        gpuContext.execute {
            gpuContext.bindTexture(15, this)
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo.get())
            GL15.glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER)

            if (sourceDataCompressed) {
                GL13.glCompressedTexImage2D(target.glTarget, mipLevel, internalFormat, width, height, 0, textureBuffer.capacity(), 0)
            } else {
                GL11.glTexImage2D(target.glTarget, mipLevel, internalFormat, width, height, 0, srcPixelFormat, GL11.GL_UNSIGNED_BYTE, 0)
            }
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
            GL15.glDeleteBuffers(pbo.get())
            val textureMaxLevel = mipmapCount - mipLevel
            if (setMaxLevel) {
                LOGGER.info("TextureMaxLevel: " + Math.max(0, textureMaxLevel))
                GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, textureMaxLevel)
            }
            gpuContext.unbindTexture(15, this)

            if (textureInfo.info.mipmapsGenerated && mipLevel == 0) {
                uploadState = OpenGlTexture.UploadState.UPLOADED
            }
        }
    }

    private fun OpenGlTexture.uploadMipMaps(textureInfo: CompleteTextureInfo, internalFormat: Int) {
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
        for (mipMapIndex in mipmapCount - 1 downTo 1) {
            val currentWidth = widths[mipMapIndex - 1]
            val currentHeight = heights[mipMapIndex - 1]
            val actual = textureInfo.data[mipMapIndex].get()
            val tempBuffer = BufferUtils.createByteBuffer(actual.size).apply {
                put(actual)
            }
            uploadWithPixelBuffer(gpuContext, textureInfo, tempBuffer, internalFormat, currentWidth, currentHeight, mipMapIndex, textureInfo.info.sourceDataCompressed, false)
        }
    }

    fun OpenGlTexture.unloadTexture() {
        if (uploadState != OpenGlTexture.UploadState.UPLOADED || preventUnload) {
            return
        }

        LOGGER.info("Unloading $path")
        uploadState = OpenGlTexture.UploadState.NOT_UPLOADED

        gpuContext.execute {
            ARBBindlessTexture.glMakeTextureHandleNonResidentARB(handle)
            LOGGER.info("Free VRAM: " + gpuContext.availableVRAM)
        }
    }

    fun postTextureChangedEvent() {
        eventBus.post(TexturesChangedEvent())
    }

    override fun clear() {

    }

    override fun update(deltaSeconds: Float) {

    }

    override fun onEntityAdded(entities: List<Entity>) {

    }

    override fun afterUpdate(deltaSeconds: Float) {

    }

    companion object {
        private val LOGGER = Logger.getLogger(TextureManager::class.java.name)
        private val TEXTURE_FACTORY_THREAD_COUNT = 1
        @Volatile @JvmField var TEXTURE_UNLOAD_THRESHOLD_IN_MS: Long = 10000
        private val USE_TEXTURE_STREAMING = false

        var executor = Executors.newFixedThreadPool(4)

        private fun convertCubeMapData(bufferedImage: BufferedImage, width: Int, height: Int, glAlphaColorModel: ColorModel, glColorModel: ColorModel): MutableList<ByteArray> {
            //        ByteBuffer imageBuffers[] = new ByteBuffer[6];
            val byteArrays = ArrayList<ByteArray>()

            var raster: WritableRaster
            var texImage: BufferedImage


            val tileWidth = width / 4
            val tileHeight = height / 3

            for (i in 0..5) {

                val topLeftBottomRight = getRectForFaceIndex(i, bufferedImage.width, bufferedImage.height)

                if (bufferedImage.colorModel.hasAlpha()) {
                    raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, tileWidth, tileHeight, 4, null)
                    texImage = BufferedImage(glAlphaColorModel, raster, false, Hashtable<Any, Any>())
                } else {
                    raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, tileWidth, tileHeight, 3, null)
                    texImage = BufferedImage(glColorModel, raster, false, Hashtable<Any, Any>())
                }

                val g = texImage.graphics
                g.color = Color(0f, 0f, 0f, 0f)
                g.fillRect(0, 0, tileWidth, tileHeight)

                g.drawImage(bufferedImage, 0, 0, tileWidth, tileHeight, topLeftBottomRight[0].x.toInt(), topLeftBottomRight[0].y.toInt(),
                        topLeftBottomRight[1].x.toInt(), topLeftBottomRight[1].y.toInt(), null)

                //            try {
                //                File outputfile = new File(i + ".png");
                //                ImageIO.write(texImage, "png", outputfile);
                //            } catch (IOException e) {
                //            	LOGGER.info("xoxoxoxo");
                //            }


                val data = (texImage.raster.dataBuffer as DataBufferByte).data
                byteArrays.add(data)

                //    		ByteBuffer tempBuffer = ByteBuffer.allocateDirect(data.length);
                //    		tempBuffer.order(ByteOrder.nativeOrder());
                //    		tempBuffer.put(data, 0, data.length);
                //    		tempBuffer.flip();
                //          imageBuffers[i] = tempBuffer;

            }
            return byteArrays
        }

        private fun getRectForFaceIndex(index: Int, imageWidth: Int, imageHeight: Int) = when (GL_TEXTURE_CUBE_MAP_POSITIVE_X +index) {

            GL_TEXTURE_CUBE_MAP_POSITIVE_X -> arrayOf(Vector2f((imageWidth / 2).toFloat(), (imageHeight / 3 + 2).toFloat()),
                        Vector2f((3 * imageWidth / 4).toFloat(), (2 * imageHeight / 3).toFloat()))

            GL_TEXTURE_CUBE_MAP_NEGATIVE_X -> arrayOf(Vector2f(0f, (imageHeight / 3).toFloat()),
                        Vector2f((imageWidth / 4).toFloat(), (2 * imageHeight / 3).toFloat()))

            GL_TEXTURE_CUBE_MAP_POSITIVE_Y -> arrayOf(Vector2f((imageWidth / 4).toFloat(), 0f),
                        Vector2f((imageWidth / 2).toFloat(), (imageHeight / 3).toFloat()))

            GL_TEXTURE_CUBE_MAP_NEGATIVE_Y -> arrayOf(Vector2f((imageWidth / 2 - 1).toFloat(), imageHeight.toFloat()),
                        Vector2f((imageWidth / 4).toFloat(), (2 * imageHeight / 3 + 1).toFloat()))

            GL_TEXTURE_CUBE_MAP_POSITIVE_Z -> arrayOf(Vector2f((3 * imageWidth / 4).toFloat(), (imageHeight / 3).toFloat()),
                        Vector2f(imageWidth.toFloat(), (2 * imageHeight / 3).toFloat()))

            GL_TEXTURE_CUBE_MAP_NEGATIVE_Z -> arrayOf(Vector2f((imageWidth / 4).toFloat(), (imageHeight / 3).toFloat()),
                        Vector2f((imageWidth / 2).toFloat(), (2 * imageHeight / 3).toFloat()))

            else -> throw IllegalStateException("")
        }

        fun deleteTexture(id: Int) {
            GL11.glDeleteTextures(id)
        }
    }

}
