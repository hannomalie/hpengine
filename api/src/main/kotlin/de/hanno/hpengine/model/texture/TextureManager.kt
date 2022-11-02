package de.hanno.hpengine.model.texture

import de.hanno.hpengine.graphics.renderer.constants.TextureTarget

interface ITextureManager {
    val cubeMap: ICubeMap
    val lensFlareTexture: Texture

    fun generateMipMaps(texture2d: TextureTarget, renderedTexture: Int)
    fun registerTextureForDebugOutput(s: String, texture2D: Texture)
}
