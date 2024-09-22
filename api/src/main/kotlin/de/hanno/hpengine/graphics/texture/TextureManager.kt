package de.hanno.hpengine.graphics.texture

import InternalTextureFormat
import com.artemis.BaseSystem
import de.hanno.hpengine.directory.AbstractDirectory
import de.hanno.hpengine.graphics.constants.MagFilter
import de.hanno.hpengine.graphics.constants.MinFilter
import de.hanno.hpengine.graphics.constants.WrapMode
import de.hanno.hpengine.model.material.Material

interface TextureManager {
    val defaultTexture: Texture
    val textures: Map<String, Texture>
    val texturesForDebugOutput : Map<String, Texture>
    val generatedCubeMaps: Map<String, CubeMap>
    val cubeMap: CubeMap
    val lensFlareTexture: Texture

    fun registerGeneratedCubeMap(s: String, texture: CubeMap)
    fun registerTextureForDebugOutput(s: String, texture2D: Texture)
    fun getTexture3D(
        gridResolution: Int,
        internalFormat: InternalTextureFormat,
        minFilter: MinFilter,
        magFilter: MagFilter,
        wrapMode: WrapMode
    ): Texture3D

    fun getTexture(
        resourcePath: String,
        srgba: Boolean = false,
        directory: AbstractDirectory,
        unloadable: Boolean
    ): Texture

    fun getTexture(
        resourcePath: String,
        srgba: Boolean = false,
        directory: AbstractDirectory
    ): Texture = getTexture(resourcePath, srgba, directory, true)

    fun setTexturesUsedInCycle(maps: Collection<Texture>, cycle: Long)
    fun getTextureUsedInCycle(texture: Texture): Long
}

abstract class TextureManagerBaseSystem: TextureManager, BaseSystem()
