package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.directory.AbstractDirectory
import de.hanno.hpengine.graphics.GpuContext
import java.io.File

data class FileBasedTexture2D(
    val path: String,
    val file: File,
    val backingTexture: OpenGLTexture2D
) : Texture2D by backingTexture {

    companion object {
        operator fun invoke(
            gpuContext: GpuContext,
            path: String,
            directory: AbstractDirectory,
            srgba: Boolean = false
        ): FileBasedTexture2D {
            return invoke(gpuContext, path, directory.resolve(path), srgba)
        }

        operator fun invoke(gpuContext: GpuContext, path: String, file: File, srgba: Boolean = false) =
            FileBasedTexture2D(path, file, OpenGLTexture2D(gpuContext, file, path, srgba))
    }
}