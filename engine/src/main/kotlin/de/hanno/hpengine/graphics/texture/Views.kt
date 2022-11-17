package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.BindlessTextures
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import org.lwjgl.opengl.ARBBindlessTexture
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL43


context(GpuContext)
fun OpenGLCubeMapArray.createViews() = (0 until dimension.depth).map { index ->
    createView(index)
}

context(GpuContext)
fun OpenGLCubeMapArray.createView(index: Int): CubeMap {
    val cubeMapView = genTextures()
    require(index < dimension.depth) { "Index out of bounds: $index / ${dimension.depth}" }

    onGpu {
        GL43.glTextureView(
            cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, id,
            internalFormat, 0, mipmapCount - 1,
            6 * index, 6
        )
    }

    val cubeMapHandle = if (isSupported(BindlessTextures)) {
        val handle = onGpu { ARBBindlessTexture.glGetTextureHandleARB(cubeMapView) }
        onGpu { ARBBindlessTexture.glMakeTextureHandleResidentARB(handle) }
        handle
    } else -1

    return OpenGLCubeMap(
        TextureDimension(dimension.width, dimension.height),
        cubeMapView,
        TextureTarget.TEXTURE_CUBE_MAP,
        internalFormat,
        cubeMapHandle,
        textureFilterConfig,
        wrapMode,
        UploadState.UPLOADED
    )
}

context(GpuContext)
fun OpenGLCubeMapArray.createView(index: Int, faceIndex: Int): OpenGLTexture2D {
    require(index < dimension.depth) { "Index out of bounds: $index / ${dimension.depth}" }
    require(faceIndex < 6) { "Index out of bounds: $faceIndex / 6" }

    val cubeMapFaceView = genTextures()

    onGpu {
        GL43.glTextureView(
            cubeMapFaceView, GL13.GL_TEXTURE_2D, id,
            internalFormat, 0, 1,
            6 * index + faceIndex, 1
        )
    }

    val cubeMapFaceHandle = if (isSupported(BindlessTextures)) {
        val handle = onGpu { ARBBindlessTexture.glGetTextureHandleARB(cubeMapFaceView) }
        onGpu { ARBBindlessTexture.glMakeTextureHandleResidentARB(handle) }
        handle
    } else -1

    return OpenGLTexture2D(
        TextureDimension(dimension.width, dimension.height),
        cubeMapFaceView,
        TextureTarget.TEXTURE_2D,
        internalFormat,
        cubeMapFaceHandle,
        textureFilterConfig,
        wrapMode,
        UploadState.UPLOADED
    )
}

context(GpuContext)
fun CubeMap.createView(faceIndex: Int): OpenGLTexture2D {
    require(faceIndex < 6) { "Index out of bounds: $faceIndex / 6" }
    val cubeMapFaceView = genTextures()
    onGpu {
        GL43.glTextureView(
            cubeMapFaceView, GL13.GL_TEXTURE_2D, id,
            internalFormat, 0, 1,
            faceIndex, 1
        )
    }

    val cubeMapFaceHandle = if (isSupported(BindlessTextures)) {
        val handle = onGpu { ARBBindlessTexture.glGetTextureHandleARB(cubeMapFaceView) }
        onGpu { ARBBindlessTexture.glMakeTextureHandleResidentARB(handle) }
        handle
    } else -1

    return OpenGLTexture2D(
        TextureDimension(dimension.width, dimension.height),
        cubeMapFaceView,
        TextureTarget.TEXTURE_2D,
        internalFormat,
        cubeMapFaceHandle,
        textureFilterConfig,
        wrapMode,
        UploadState.UPLOADED
    )
}