package de.hanno.hpengine.engine.model.texture

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.TexturesChangedEvent
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.*
import de.hanno.hpengine.engine.graphics.shader.ComputeShaderProgram
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.texture.Texture.Companion.autoConvertToDDS
import de.hanno.hpengine.engine.model.texture.Texture.Companion.getFullPathAsDDS
import de.hanno.hpengine.engine.model.texture.Texture.Companion.textureAvailableAsDDS
import de.hanno.hpengine.engine.threads.TimeStepThread
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.commandqueue.CommandQueue
import de.hanno.hpengine.util.commandqueue.FutureCallable
import jogl.DDSImage
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.joml.Vector2f
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL13.*
import java.awt.Color
import java.awt.color.ColorSpace
import java.awt.image.*
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.logging.Logger
import javax.imageio.ImageIO

/**
 * A utility class to load textures for JOGL. This source is based
 * on a texture that can be found in the Java Gaming (www.javagaming.org)
 * Wiki. It has been simplified slightly for explicit 2D graphics use.
 *
 * OpenGL uses a particular image format. Since the images that are
 * loaded from disk may not match this format this loader introduces
 * a intermediate image which the source image is copied into. In turn,
 * this image is used as source for the OpenGL texture.
 *
 * @author Kevin Glass
 * @author Brian Matzon
 */
class TextureManager(private val eventBus: EventBus, programManager: ProgramManager,
                     val gpuContext: GpuContext) : Manager {

    @Volatile
    lateinit var defaultTextureAsBufferedImage: BufferedImage
        private set

    val commandQueue = CommandQueue()

    val lensFlareTexture: ITexture<*>?
    var cubeMap: CubeMap? = null
    private val blur2dProgramSeperableHorizontal: ComputeShaderProgram
    private val blur2dProgramSeperableVertical: ComputeShaderProgram

    /** The table of textures that have been loaded in this loader  */
    var textures: MutableMap<String, ITexture<*>> = ConcurrentHashMap()

    private val loadingTextures = HashSet<String>()

    /** The colour model including alpha for the GL image  */
    private val glAlphaColorModel: ColorModel

    /** The colour model for the GL image  */
    private val glColorModel: ColorModel

    /**
     * Create a new de.hanno.de.hanno.hpengine.texture loader based on the game panel
     *
     */
    lateinit var defaultTexture: ITexture<*>
        internal set

    init {
        println("TextureManager constructor")
        GpuContext.exitOnGLError("Begin TextureManager constructor")
        glAlphaColorModel = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                intArrayOf(8, 8, 8, 8),
                true,
                false,
                ComponentColorModel.TRANSLUCENT,
                DataBuffer.TYPE_BYTE)

        glColorModel = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                intArrayOf(8, 8, 8, 0),
                false,
                false,
                ComponentColorModel.OPAQUE,
                DataBuffer.TYPE_BYTE)

        //    	loadAllAvailableTextures();

        val horizontalDefines = object : Defines() {
            init {
                add(Define.getDefine("HORIZONTAL", true))
            }
        }
        val verticalDefines = object : Defines() {
            init {
                add(Define.getDefine("VERTICAL", true))
            }
        }
        blur2dProgramSeperableHorizontal = programManager.getComputeProgram("blur2D_seperable_vertical_or_horizontal_compute.glsl", horizontalDefines)
        blur2dProgramSeperableVertical = programManager.getComputeProgram("blur2D_seperable_vertical_or_horizontal_compute.glsl", verticalDefines)

        GpuContext.exitOnGLError("After TextureManager constructor")

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

        loadDefaultTexture()
        GpuContext.exitOnGLError("After loadDefaultTexture")
        lensFlareTexture = getTexture("hp/assets/textures/lens_flare_tex.jpg", true)
        GpuContext.exitOnGLError("After load lensFlareTexture")
        try {
            cubeMap = getCubeMap("hp/assets/textures/skybox.png")
            GpuContext.exitOnGLError("After load cubemap")
            this.gpuContext.activeTexture(0)
            //            instance.generateMipMapsCubeMap(cubeMap.getTextureId());
        } catch (e: IOException) {
            LOGGER.severe(e.message)
        }

    }

    fun loadDefaultTexture() {
        val defaultTexturePath = "hp/assets/models/textures/gi_flag.png"
        defaultTexture = getTexture(defaultTexturePath, true)
        try {
            defaultTextureAsBufferedImage = loadImage(defaultTexturePath) ?: throw IllegalStateException("Cannot load default texture!")
        } catch (e: IOException) {
            e.printStackTrace()
            System.exit(-1)
        }

    }

    private fun loadAllAvailableTextures() {
        val textureDir = File(Texture.directory)
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

    /**
     * Create a new de.hanno.de.hanno.hpengine.texture ID
     *
     * @return A new de.hanno.de.hanno.hpengine.texture ID
     */
    private fun genTextures(): Int {
        return gpuContext.genTextures()
    }

    @JvmOverloads
    fun getTexture(resourceName: String, srgba: Boolean = false): ITexture<*> {
        if (textureLoaded(resourceName)) {
            return textures[resourceName]!!
        }
        if (!loadingTextures.contains(resourceName)) {
            loadingTextures.add(resourceName)
        } else {
            while (!textureLoaded(resourceName)) {
                LOGGER.info("Waiting for texture $resourceName to become available...")
                try {
                    Thread.sleep(10)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

            }
            return textures[resourceName]!!
        }
        LOGGER.info("$resourceName requested")

        return convertAndUpload(resourceName, srgba)
    }

    fun convertAndUpload(resourceName: String, srgba: Boolean): ITexture<*> {
        val texture = commandQueue.calculate(object : FutureCallable<Texture>() {
            @Throws(Exception::class)
            override fun execute(): Texture? {
                return getTextureXXX(resourceName, resourceName, srgba)
            }
        })

        return if (texture != null) {
            textures[resourceName] = texture
            texture
        } else {
            LOGGER.severe("No texture loaded for $resourceName")
            defaultTexture
        }
    }

    private fun getTextureXXX(path: String, resourceName: String, srgba: Boolean): Texture? {
        val ddsImageAvailable = textureAvailableAsDDS(path)
        val imageExists = File(path).exists()
        LOGGER.info("$path available as dds: $ddsImageAvailable")
        val height: Int
        val width: Int
        val mipMapCount: Int
        var srcPixelFormat = GL11.GL_RGB
        var mipmapsGenerated = false
        var sourceDataCompressed = false
        val data: List<ByteArray>
        val textureBuffer: ByteBuffer
        var result: Texture? = null
        try {
            val start = System.currentTimeMillis()
            if (imageExists) {
                if (ddsImageAvailable) {
                    val ddsImage = DDSImage.read(File(getFullPathAsDDS(path)))
                    sourceDataCompressed = true
                    height = ddsImage.height
                    width = ddsImage.width
                    val mipMapCountPlusOne = Util.calculateMipMapCountPlusOne(width, height)
                    mipMapCount = mipMapCountPlusOne - 1

                    val intRange = 0 until ddsImage.allMipMaps.size
                    data = intRange.map {
                        val info = ddsImage.getMipMap(it)
//                        BufferedImage mipmapimage = DDSUtil.decompressTexture(info.getData(), info.getWidth(), info.getHeight(), info.getCompressionFormat());
//                        showAsTextureInFrame(mipmapImage);
//                        data[i] = TextureManager.getInstance().convertImageData(mipmapImage);
                        val array = ByteArray(info.data.capacity())
                        info.data.get(array)
                        array
                    }
                    if (ddsImage.numMipMaps > 1) {
                        mipmapsGenerated = true
                    }
                    textureBuffer = Texture.buffer(data[0])
                } else {
                    val bufferedImage = {
                        val image = this@TextureManager.loadImage(path)
                        if (image != null && autoConvertToDDS) {
                            Thread {
                                try {
                                    Texture.saveAsDDS(path, image)
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                }
                            }.start()
                        }
                        image ?: {
                            LOGGER.severe("Texture $path cannot be read, default texture returned instead...")
                            defaultTextureAsBufferedImage
                        }()
                    }()

                    width = bufferedImage.width
                    height = bufferedImage.height
                    val mipMapCountPlusOne = Util.calculateMipMapCountPlusOne(width, height)
                    mipMapCount = mipMapCountPlusOne - 1

                    if (bufferedImage.colorModel.hasAlpha()) {
                        srcPixelFormat = GL11.GL_RGBA
                    }

                    data = listOf(this@TextureManager.convertImageData(bufferedImage))

                    textureBuffer = Texture.buffer(data[0])
                }

                val texture = Texture(this@TextureManager,
                        resourceName,
                        srgba,
                        width,
                        height,
                        mipMapCount,
                        mipmapsGenerated,
                        srcPixelFormat,
                        sourceDataCompressed,
                        data.toTypedArray(), genTextures(), GL11.GL_LINEAR_MIPMAP_LINEAR, GL11.GL_LINEAR)
                texture.upload(textureBuffer)
                result = texture
            }
            LOGGER.info("" + (System.currentTimeMillis() - start) + "ms for loading and uploading as dds with mipmaps: " + path)
            postTextureChangedEvent()
            return result
        } catch (e: IOException) {
            e.printStackTrace()
            LOGGER.severe("Texture not found: $path. Default de.hanno.hpengine.texture returned...")
            return null
        } catch (e: NullPointerException) {
            e.printStackTrace()
            LOGGER.severe("Texture not found: $path. Default de.hanno.hpengine.texture returned...")
            return null
        }

    }

    private fun textureLoaded(resourceName: String): Boolean {
        return textures.containsKey(resourceName)
    }

    fun texturePreCompiled(resourceName: String): Boolean {
        val fileName = FilenameUtils.getBaseName(resourceName)
        val f = File(Texture.directory + fileName + ".hptexture")
        return f.exists()
    }


    private fun cubeMapPreCompiled(resourceName: String): Boolean {
        val fileName = FilenameUtils.getBaseName(resourceName)
        val f = File(Texture.directory + fileName + ".hpcubemap")
        return f.exists()
    }

    @Throws(IOException::class)
    fun getCubeMap(resourceName: String): CubeMap? {
        var tex: CubeMap? = textures[resourceName + "_cube"] as CubeMap?

        if (tex != null) {
            return tex
        }

        tex = getCubeMap(resourceName,
                GL11.GL_RGBA, // dst pixel format
                GL11.GL_LINEAR_MIPMAP_LINEAR, // min filter (unused)
                GL11.GL_LINEAR, false)

        textures[resourceName + "_cube"] = tex
        return tex
    }

    @Throws(IOException::class)
    private fun getCubeMap(resourceName: String,
                           dstPixelFormat: Int,
                           minFilter: Int,
                           magFilter: Int, asStream: Boolean): CubeMap {

        var srcPixelFormat = 0

        var bufferedImage: BufferedImage? = null
        if (asStream) {
            bufferedImage = loadImageAsStream(resourceName)
        } else {
            bufferedImage = loadImage(resourceName)
        }

        if (bufferedImage!!.colorModel.hasAlpha()) {
            srcPixelFormat = GL11.GL_RGBA
        } else {
            srcPixelFormat = GL11.GL_RGB
        }
        // create the de.hanno.de.hanno.hpengine.texture ID for this de.hanno.de.hanno.hpengine.texture
        val textureID = genTextures()

        val width = bufferedImage.width
        val height = bufferedImage.height

        val data = convertCubeMapData(bufferedImage, width, height, glAlphaColorModel, glColorModel)

        val cubeMap = CubeMap(this, resourceName, width, height, minFilter, magFilter, srcPixelFormat, dstPixelFormat, genTextures(), data)
        cubeMap.bind()
        upload(cubeMap)

        return cubeMap
    }

    fun upload(cubeMap: CubeMap) {

        gpuContext.execute {
            cubeMap.bind()
            //        if (target == GL13.GL_TEXTURE_CUBE_MAP)
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

            cubeMap.load(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X, cubeMap.buffer(perFaceBuffer, cubeMap.getData()[1])) //1
            cubeMap.load(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X, cubeMap.buffer(perFaceBuffer, cubeMap.getData()[0])) //0
            cubeMap.load(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, cubeMap.buffer(perFaceBuffer, cubeMap.getData()[2]))
            cubeMap.load(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y, cubeMap.buffer(perFaceBuffer, cubeMap.getData()[3]))
            cubeMap.load(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, cubeMap.buffer(perFaceBuffer, cubeMap.getData()[4]))
            cubeMap.load(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z, cubeMap.buffer(perFaceBuffer, cubeMap.getData()[5]))

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

    private fun generateMipMaps(texture: Texture, mipmap: Boolean) {
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
        val textureId = genTextures()
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
            blur2dProgramSeperableHorizontal.use()
            gpuContext.bindTexture(0, TEXTURE_2D, sourceTexture)
            gpuContext.bindImageTexture(1, sourceTexture, mipmapTarget, false, mipmapTarget, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F)
            blur2dProgramSeperableHorizontal.setUniform("width", finalWidth)
            blur2dProgramSeperableHorizontal.setUniform("height", finalHeight)
            blur2dProgramSeperableHorizontal.setUniform("mipmapSource", mipmapSource)
            blur2dProgramSeperableHorizontal.setUniform("mipmapTarget", mipmapTarget)
            blur2dProgramSeperableHorizontal.dispatchCompute(finalWidth / 8, finalHeight / 8, 1)

            blur2dProgramSeperableVertical.use()
            //            OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, sourceTexture);
            //            OpenGLContext.getInstance().bindImageTexture(1,sourceTexture, mipmapTarget, false, mipmapTarget, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
            blur2dProgramSeperableVertical.setUniform("width", finalWidth)
            blur2dProgramSeperableVertical.setUniform("height", finalHeight)
            blur2dProgramSeperableVertical.setUniform("mipmapSource", mipmapSource)
            blur2dProgramSeperableVertical.setUniform("mipmapTarget", mipmapTarget)
            blur2dProgramSeperableVertical.dispatchCompute(finalWidth / 8, finalHeight / 8, 1)
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
            blur2dProgramSeperableHorizontal.use()
            gpuContext.bindTexture(0, TEXTURE_2D, sourceTexture)
            gpuContext.bindImageTexture(1, sourceTexture, mipmapTarget, false, mipmapTarget, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F)
            blur2dProgramSeperableHorizontal.setUniform("width", finalWidth)
            blur2dProgramSeperableHorizontal.setUniform("height", finalHeight)
            blur2dProgramSeperableHorizontal.setUniform("mipmapSource", mipmapSource)
            blur2dProgramSeperableHorizontal.setUniform("mipmapTarget", mipmapTarget)
            val num_groups_x = Math.max(1, finalWidth / 8)
            val num_groups_y = Math.max(1, finalHeight / 8)
            blur2dProgramSeperableHorizontal.dispatchCompute(num_groups_x, num_groups_y, 1)
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
