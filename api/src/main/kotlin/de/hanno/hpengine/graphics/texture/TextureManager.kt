package de.hanno.hpengine.graphics.texture

import com.artemis.BaseSystem
import de.hanno.hpengine.directory.AbstractDirectory
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.ImageReader

interface TextureManager {
    val defaultTexture: StaticFileBasedTexture2D
    val textures: Map<String, Texture>
    val fileBasedTextures: Map<String, FileBasedTexture2D>
    val texturesForDebugOutput : Map<String, Texture>
    val generatedCubeMaps: Map<String, CubeMap>

    fun registerGeneratedCubeMap(s: String, texture: CubeMap)
    fun registerTextureForDebugOutput(s: String, texture2D: Texture)

    fun getTexture3D(
        description: TextureDescription.Texture3DDescription,
    ): Texture3D

    fun getStaticTextureHandle(
        resourcePath: String,
        srgba: Boolean = false,
        directory: AbstractDirectory,
    ): StaticFileBasedTexture2D

    fun getTexture(
        resourcePath: String,
        srgba: Boolean = false,
        directory: AbstractDirectory,
        unloadable: Boolean,
    ): FileBasedTexture2D

    val FileBasedTexture2D.canBeUnloaded: Boolean
    val texturePool: List<DynamicFileBasedTexture2D>
    fun getCubeMap(resourceName: String, file: File, srgba: Boolean = true): CubeMap
}

// https://stackoverflow.com/questions/1559253/java-imageio-getting-image-dimensions-without-reading-the-entire-file
fun getImageDimension(file: File): TextureDimension2D {
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
data class TextureUsageInfo(
    var time: Long?,
    var distance: Float,
    var behindCamera: Boolean,
    var cameraIsInside: Boolean,
    var cycle: Long?
)

abstract class TextureManagerBaseSystem: TextureManager, BaseSystem()
