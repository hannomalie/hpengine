package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.directory.AbstractDirectory
import de.hanno.hpengine.graphics.GraphicsApi
import java.io.File

data class FileBasedTexture2D(
    val path: String,
    val file: File,
    val backingTexture: OpenGLTexture2D
) : Texture2D by backingTexture {

    companion object {

        context(GraphicsApi)
        operator fun invoke(
            path: String,
            directory: AbstractDirectory,
            srgba: Boolean = false
        ) = invoke(path, directory.resolve(path), srgba)

        context(GraphicsApi)
        operator fun invoke(path: String, file: File, srgba: Boolean = false) = FileBasedTexture2D(
            path, file, OpenGLTexture2D(file, srgba)
        )
    }
}