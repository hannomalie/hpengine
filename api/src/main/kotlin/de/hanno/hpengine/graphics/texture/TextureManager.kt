package de.hanno.hpengine.graphics.texture

import com.artemis.BaseSystem
import de.hanno.hpengine.directory.AbstractDirectory

interface TextureManager {
    val defaultTexture: StaticFileBasedTexture2D
    val textures: Map<String, Texture>
    val fileBasedTextures: Map<String, FileBasedTexture2D>
    val texturesForDebugOutput : Map<String, Texture>
    val generatedCubeMaps: Map<String, CubeMap>
    val cubeMap: StaticHandle<CubeMap>
    val lensFlareTexture: Texture

    fun registerGeneratedCubeMap(s: String, texture: CubeMap)
    fun registerTextureForDebugOutput(s: String, texture2D: Texture)

    fun getTexture3D(
        description: TextureDescription.Texture3DDescription,
    ): Texture3D

    fun getStaticTextureHandle(
        resourcePath: String,
        srgba: Boolean = false,
        directory: AbstractDirectory,
    ): StaticFileBasedTexture2D

    fun getTexture(
        resourcePath: String,
        srgba: Boolean = false,
        directory: AbstractDirectory,
        unloadable: Boolean,
    ): FileBasedTexture2D

    fun setTexturesUsedInCycle(
        texture: TextureHandle<*>,
        cycle: Long?,
        distance: Float,
        isBehindCamera: Boolean
    )
    fun getTextureUsedInCycle(texture: TextureHandle<*>): TextureUsageInfo?

    val FileBasedTexture2D.canBeUnloaded: Boolean
    val texturePool: List<DynamicFileBasedTexture2D>
}

data class TextureUsageInfo(
    var time: Long?,
    var distance: Float,
    var behindCamera: Boolean,
    var cameraIsInside: Boolean,
    var cycle: Long?
)

abstract class TextureManagerBaseSystem: TextureManager, BaseSystem()
