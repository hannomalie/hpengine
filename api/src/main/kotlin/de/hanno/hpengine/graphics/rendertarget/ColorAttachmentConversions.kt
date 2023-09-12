package de.hanno.hpengine.graphics.rendertarget


import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.constants.WrapMode
import de.hanno.hpengine.graphics.texture.CubeMap
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.graphics.texture.TextureDimension
import de.hanno.hpengine.graphics.texture.UploadState

fun List<ColorAttachmentDefinition>.toTextures(
    graphicsApi: GraphicsApi,
    width: Int,
    height: Int
): List<Texture2D> = map {
    graphicsApi.Texture2D(
        TextureDimension(width, height),
        TextureTarget.TEXTURE_2D,
        it.internalFormat,
        it.textureFilter,
        WrapMode.Repeat,
        UploadState.Uploaded
    )
}

fun List<ColorAttachmentDefinition>.toCubeMaps(
    graphicsApi: GraphicsApi,
    width: Int, height: Int
): List<CubeMap> = map {
    graphicsApi.CubeMap(
        dimension = TextureDimension(width, height),
        textureFilterConfig = it.textureFilter,
        internalFormat = it.internalFormat,
        wrapMode = WrapMode.Repeat,
    )
}

fun List<ColorAttachmentDefinition>.toCubeMapArrays(
    graphicsApi: GraphicsApi,
    width: Int,
    height: Int,
    depth: Int
) = map {
    graphicsApi.CubeMapArray(
        TextureDimension(width, height, depth),
        it.textureFilter,
        it.internalFormat,
        WrapMode.Repeat,
    )
}
