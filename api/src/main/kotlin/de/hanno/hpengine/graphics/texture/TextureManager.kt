package de.hanno.hpengine.graphics.texture

import com.artemis.BaseSystem

interface TextureManager {
    val defaultTexture: Texture
    val textures: Map<String, Texture>
    val texturesForDebugOutput : Map<String, Texture>
    val cubeMap: CubeMap
    val lensFlareTexture: Texture

    fun registerTextureForDebugOutput(s: String, texture2D: Texture)
}

abstract class TextureManagerBaseSystem: TextureManager, BaseSystem()
