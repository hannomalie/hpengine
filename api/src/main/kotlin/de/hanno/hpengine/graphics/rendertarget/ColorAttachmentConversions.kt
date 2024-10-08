package de.hanno.hpengine.graphics.rendertarget


import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.WrapMode
import de.hanno.hpengine.graphics.texture.CubeMap
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.graphics.texture.TextureDescription
import de.hanno.hpengine.graphics.texture.TextureDescription.Texture2DDescription
import de.hanno.hpengine.graphics.texture.TextureDimension

fun List<ColorAttachmentDefinition>.toTextures(
    graphicsApi: GraphicsApi,
    width: Int,
    height: Int
): List<Texture2D> = map {
    graphicsApi.Texture2D(
        Texture2DDescription(
            TextureDimension(width, height),
            it.internalFormat,
            it.textureFilter,
            WrapMode.Repeat
        )
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
        TextureDescription.CubeMapArrayDescription(
            TextureDimension(width, height, depth),
            it.internalFormat,
            it.textureFilter,
            WrapMode.Repeat,
        )
    )
}
