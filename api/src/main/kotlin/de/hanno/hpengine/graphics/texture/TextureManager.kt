package de.hanno.hpengine.graphics.texture

interface TextureManager {
    val defaultTexture: Texture
    val textures: Map<String, Texture>
    val texturesForDebugOutput : Map<String, Texture>
    val cubeMap: CubeMap
    val lensFlareTexture: Texture

    fun registerTextureForDebugOutput(s: String, texture2D: Texture)
}
