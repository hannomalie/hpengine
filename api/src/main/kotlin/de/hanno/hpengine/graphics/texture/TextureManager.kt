package de.hanno.hpengine.graphics.texture

interface TextureManager {
    val cubeMap: CubeMap
    val lensFlareTexture: Texture

    fun registerTextureForDebugOutput(s: String, texture2D: Texture)
}
