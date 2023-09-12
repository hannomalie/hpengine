package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.directory.AbstractDirectory
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.texture.DDSConverter.rescaleToNextPowerOfTwo
import de.hanno.hpengine.graphics.texture.OpenGLTexture2D.Companion.uploadAsync
import java.io.File
import javax.imageio.ImageIO

data class FileBasedTexture2D(
    val path: String,
    val file: File,
    val backingTexture: OpenGLTexture2D
) : Texture2D by backingTexture {

    fun uploadAsync() {
        val bufferedImage = ImageIO.read(file).apply { DDSConverter.run { rescaleToNextPowerOfTwo() } }
        backingTexture.uploadAsync(
            backingTexture.graphicsApi,
            bufferedImage,
            backingTexture.internalFormat == InternalTextureFormat.SRGB8_ALPHA8_EXT,
            backingTexture.internalFormat
        )
    }

    companion object {

        operator fun invoke(
            graphicsApi: GraphicsApi,
            path: String,
            directory: AbstractDirectory,
            srgba: Boolean = false
        ) = invoke(graphicsApi, path, directory.resolve(path), srgba)

        operator fun invoke(
            graphicsApi: GraphicsApi, path: String, file: File, srgba: Boolean = false) = OpenGLTexture2D(graphicsApi, file, path, srgba)
    }
}