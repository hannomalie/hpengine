package de.hanno.hpengine.graphics.texture

import de.hanno.hpengine.graphics.renderer.constants.TextureTarget

interface TextureManager {
    val cubeMap: CubeMap
    val lensFlareTexture: Texture

    fun generateMipMaps(texture2d: TextureTarget, renderedTexture: Int)
    fun registerTextureForDebugOutput(s: String, texture2D: Texture)
}
