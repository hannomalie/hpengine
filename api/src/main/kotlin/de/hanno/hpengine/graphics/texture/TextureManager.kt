package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import com.artemis.BaseSystem
import de.hanno.hpengine.graphics.constants.MagFilter
import de.hanno.hpengine.graphics.constants.MinFilter
import de.hanno.hpengine.graphics.constants.WrapMode

interface TextureManager {
    val defaultTexture: Texture
    val textures: Map<String, Texture>
    val texturesForDebugOutput : Map<String, Texture>
    val cubeMap: CubeMap
    val lensFlareTexture: Texture

    fun registerTextureForDebugOutput(s: String, texture2D: Texture)
    fun getTexture3D(
        gridResolution: Int,
        internalFormat: InternalTextureFormat,
        minFilter: MinFilter,
        magFilter: MagFilter,
        wrapMode: WrapMode
    ): Texture3D
}

abstract class TextureManagerBaseSystem: TextureManager, BaseSystem()
