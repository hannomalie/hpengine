package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.constants.glTarget
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.joml.Vector2i
import org.lwjgl.opengl.*
import java.awt.Color
import java.awt.image.*
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

data class FileBasedOpenGLCubeMap(
    val path: String,
    val backingTexture: CubeMap,
    val files: List<File>,
    val isBgrFormat: Boolean = false
) : CubeMap by backingTexture {
    init {
        require(files.isNotEmpty()) { "Cannot create CubeMap without any files!" }
        require(files.size == 1 || files.size == 6) { "Pass either 1 or 6 images to create a CubeMap!" }
    }

    val backingFileMode = if (files.size == 1) CubeMapFileDataFormat.Single else CubeMapFileDataFormat.Six
    val file = files.first()
    val bufferedImage: BufferedImage = ImageIO.read(file)
    val srcPixelFormat = if (bufferedImage.colorModel.hasAlpha()) {
        if (isBgrFormat) GL12.GL_BGRA else GL11.GL_RGBA
    } else {
        if (isBgrFormat) GL12.GL_BGR else GL11.GL_RGB
    }
    val width = bufferedImage.width
    val height = bufferedImage.height
    val tileDimension = if (backingFileMode == CubeMapFileDataFormat.Six) {
        TextureDimension(width, height)
    } else TextureDimension(width / 4, height / 3)

    constructor(path: String, backingTexture: CubeMap, vararg file: File) : this(path, backingTexture, file.toList())

    fun load(gpuContext: GpuContext) = CompletableFuture.supplyAsync {
        if (backingFileMode == CubeMapFileDataFormat.Six) {
            GlobalScope.launch {
                files.map {
                    GlobalScope.async {
                        ImageIO.read(it)
                    }
                }.awaitAll().let { upload(gpuContext, it.getCubeMapUploadInfo()) }
            }
        } else {
            val bufferedImage: BufferedImage = ImageIO.read(file)
            upload(gpuContext, bufferedImage.getCubeMapUploadInfo())
        }
    }

    fun upload(gpuContext: GpuContext, cubeMapFaceTarget: Int, buffer: ByteBuffer) = gpuContext.invoke {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
        GL11.glTexSubImage2D(
            cubeMapFaceTarget,
            0,
            0,
            0,
            tileDimension.width,
            tileDimension.height,
            srcPixelFormat,
            GL11.GL_UNSIGNED_BYTE,
            buffer
        )
    }

    fun upload(gpuContext: GpuContext, info: UploadInfo.CubeMapUploadInfo) = gpuContext.invoke {
        gpuContext.bindTexture(this)

        upload(gpuContext, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X, info.buffers[0])
        upload(gpuContext, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X, info.buffers[1])
        upload(gpuContext, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, info.buffers[2])
        upload(gpuContext, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y, info.buffers[3])
        upload(gpuContext, GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, info.buffers[4])
        upload(gpuContext, GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z, info.buffers[5])

        gpuContext.invoke {
            GL30.glGenerateMipmap(TextureTarget.TEXTURE_CUBE_MAP.glTarget)
        }
    }

    companion object {

        operator fun invoke(
            gpuContext: GpuContext,
            path: String,
            files: List<File>,
            srgba: Boolean = false
        ): FileBasedOpenGLCubeMap {
            val internalFormat: Int =
                if (srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT

            val bufferedImage: BufferedImage = ImageIO.read(files.first())
            val width = bufferedImage.width
            val height = bufferedImage.height
            val tileDimension = TextureDimension(width, height)

            val isBgrFormat = bufferedImage.isBgrFormat

            val fileBasedCubeMap = FileBasedOpenGLCubeMap(
                path,
                OpenGLCubeMap(gpuContext, tileDimension, TextureFilterConfig(), internalFormat, GL11.GL_REPEAT),
                files,
                isBgrFormat
            )

            return fileBasedCubeMap.apply {
                load(gpuContext)
            }
        }

        private val BufferedImage.isBgrFormat: Boolean
            get() = type == BufferedImage.TYPE_INT_BGR || type == BufferedImage.TYPE_3BYTE_BGR || type == BufferedImage.TYPE_4BYTE_ABGR || type == BufferedImage.TYPE_4BYTE_ABGR_PRE

        operator fun invoke(
            gpuContext: GpuContext,
            path: String,
            file: File,
            srgba: Boolean = false
        ): FileBasedOpenGLCubeMap {
            val internalFormat: Int =
                if (srgba) EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT else EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT

            val bufferedImage: BufferedImage = ImageIO.read(file)
            val width = bufferedImage.width
            val height = bufferedImage.height
            val tileDimension = TextureDimension(width / 4, height / 3)

            val fileBasedCubeMap = FileBasedOpenGLCubeMap(
                path,
                OpenGLCubeMap(gpuContext, tileDimension, TextureFilterConfig(), internalFormat, GL11.GL_REPEAT),
                file
            )

            return fileBasedCubeMap.apply {
                load(gpuContext)
            }
        }
    }
}

private fun List<BufferedImage>.getCubeMapUploadInfo() = UploadInfo.CubeMapUploadInfo(
    TextureDimension(first().width, first().height),
    toByteArrays().map { byteArray ->
        ByteBuffer.allocateDirect(byteArray.size).apply {
            buffer(byteArray)
        }
    }
)

fun List<BufferedImage>.toByteArrays() = map { image ->
    (image.raster.dataBuffer as DataBufferByte).data
}

private fun BufferedImage.getCubeMapUploadInfo(): UploadInfo.CubeMapUploadInfo {
    val tileDimension = TextureDimension(width / 4, height / 3)

    val data = convertCubeMapData()

    val buffers = data.map { byteArray ->
        ByteBuffer.allocateDirect(byteArray.size).apply {
            buffer(byteArray)
        }
    }
    return UploadInfo.CubeMapUploadInfo(tileDimension, buffers)
}

fun BufferedImage.convertCubeMapData(): List<ByteArray> {
    val tileDimension = TextureDimension(width / 4, height / 3)
    //        ByteBuffer imageBuffers[] = new ByteBuffer[6];
    val byteArrays = ArrayList<ByteArray>()

    val tileWidth = tileDimension.width
    val tileHeight = tileDimension.height

    for (i in 0..5) {

        val topLeftBottomRight = getRectForFaceIndex(i, tileWidth, tileHeight)

        val texImage = if (colorModel.hasAlpha()) {
            val raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, tileWidth, tileHeight, 4, null)
            BufferedImage(glAlphaColorModel, raster, false, Hashtable<Any, Any>())
        } else {
            val raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, tileWidth, tileHeight, 3, null)
            BufferedImage(glColorModel, raster, false, Hashtable<Any, Any>())
        }

        val graphics = texImage.graphics
        graphics.color = Color(0f, 0f, 0f, 0f)
        graphics.fillRect(0, 0, tileWidth, tileHeight)

        graphics.drawImage(
            this, 0, 0, tileWidth, tileHeight,
            topLeftBottomRight[0].x, topLeftBottomRight[0].y,
            topLeftBottomRight[1].x, topLeftBottomRight[1].y, null
        )

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

// Why do i have to do +1 and -1 here?
private fun getRectForFaceIndex(
    index: Int,
    tileWidth: Int,
    tileHeight: Int
) = when (GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + index) {
    GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X -> arrayOf(Vector2i(0, tileHeight), Vector2i(tileWidth, 2 * tileHeight))
    GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X -> arrayOf(
        Vector2i(2 * tileWidth, tileHeight),
        Vector2i(3 * tileWidth, 2 * tileHeight)
    )
    GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y -> arrayOf(
        Vector2i(2 * tileWidth - 1, tileHeight),
        Vector2i(tileWidth, 0)
    )
    GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y -> arrayOf(
        Vector2i(2 * tileWidth, 3 * tileHeight),
        Vector2i(tileWidth, 2 * tileHeight)
    )
    GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z -> arrayOf(
        Vector2i(3 * tileWidth, tileHeight),
        Vector2i(4 * tileWidth, 2 * tileHeight)
    )
    GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z -> arrayOf(
        Vector2i(tileWidth, tileHeight),
        Vector2i(2 * tileWidth, 2 * tileHeight)
    )
    else -> throw IllegalStateException("")
}
