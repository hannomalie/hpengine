package de.hanno.hpengine.graphics.renderer.rendertarget


import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.constants.WrapMode
import de.hanno.hpengine.graphics.texture.*

context(GraphicsApi)
fun List<ColorAttachmentDefinition>.toTextures(
    width: Int,
    height: Int
): List<Texture2D> = map {
    Texture2D(
        TextureDimension(width, height),
        TextureTarget.TEXTURE_2D,
        it.internalFormat,
        it.textureFilter,
        WrapMode.Repeat,
        UploadState.UPLOADED
    )
}

context(GraphicsApi)
fun List<ColorAttachmentDefinition>.toCubeMaps(width: Int, height: Int): List<CubeMap> = map {
    CubeMap(
        dimension = TextureDimension(width, height),
        textureFilterConfig = it.textureFilter,
        internalFormat = it.internalFormat,
        wrapMode = WrapMode.Repeat,
    )
}

context(GraphicsApi)
fun List<ColorAttachmentDefinition>.toCubeMapArrays(
    width: Int,
    height: Int,
    depth: Int
) = map {
    CubeMapArray(
        TextureDimension(width, height, depth),
        it.textureFilter,
        it.internalFormat,
        WrapMode.Repeat,
    )
}
