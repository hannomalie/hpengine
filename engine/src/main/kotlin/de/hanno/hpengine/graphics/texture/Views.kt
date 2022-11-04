package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.BindlessTextures
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import org.lwjgl.opengl.ARBBindlessTexture
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL43

fun OpenGLCubeMapArray.createViews(gpuContext: GpuContext): List<CubeMap> {
    return (0 until dimension.depth).map { index ->
        createView(gpuContext, index)
    }
}

fun OpenGLCubeMapArray.createView(gpuContext: GpuContext, index: Int): CubeMap {
    val cubeMapView = gpuContext.genTextures()
    require(index < dimension.depth) { "Index out of bounds: $index / ${dimension.depth}" }

    gpuContext.invoke {
        GL43.glTextureView(
            cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, id,
            internalFormat, 0, mipmapCount - 1,
            6 * index, 6
        )
    }

    val cubeMapHandle = if (gpuContext.isSupported(BindlessTextures)) {
        val handle = gpuContext.invoke { ARBBindlessTexture.glGetTextureHandleARB(cubeMapView) }
        gpuContext.invoke { ARBBindlessTexture.glMakeTextureHandleResidentARB(handle) }
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

fun OpenGLCubeMapArray.createView(gpuContext: GpuContext, index: Int, faceIndex: Int): OpenGLTexture2D {
    val cubeMapFaceView = gpuContext.genTextures()
    require(index < dimension.depth) { "Index out of bounds: $index / ${dimension.depth}" }
    require(faceIndex < 6) { "Index out of bounds: $faceIndex / 6" }
    gpuContext.invoke {
        GL43.glTextureView(
            cubeMapFaceView, GL13.GL_TEXTURE_2D, id,
            internalFormat, 0, 1,
            6 * index + faceIndex, 1
        )
    }

    val cubeMapFaceHandle = if (gpuContext.isSupported(BindlessTextures)) {
        val handle = gpuContext.invoke { ARBBindlessTexture.glGetTextureHandleARB(cubeMapFaceView) }
        gpuContext.invoke { ARBBindlessTexture.glMakeTextureHandleResidentARB(handle) }
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

fun CubeMap.createView(gpuContext: GpuContext, faceIndex: Int): OpenGLTexture2D {
    val cubeMapFaceView = gpuContext.genTextures()
    require(faceIndex < 6) { "Index out of bounds: $faceIndex / 6" }
    gpuContext.invoke {
        GL43.glTextureView(
            cubeMapFaceView, GL13.GL_TEXTURE_2D, id,
            internalFormat, 0, 1,
            faceIndex, 1
        )
    }

    val cubeMapFaceHandle = if (gpuContext.isSupported(BindlessTextures)) {
        val handle = gpuContext.invoke { ARBBindlessTexture.glGetTextureHandleARB(cubeMapFaceView) }
        gpuContext.invoke { ARBBindlessTexture.glMakeTextureHandleResidentARB(handle) }
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