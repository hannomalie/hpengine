package de.hanno.hpengine.graphics.renderer.pipelines

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.model.material.Material

fun Program<*>.setTextureUniforms(
    graphicsApi: GraphicsApi,
    maps: MutableMap<Material.MAP, TextureHandle<*>>,
    diffuseFallbackTexture: Texture? = null
) = graphicsApi.run {
    for (mapEnumEntry in Material.MAP.entries) {
        when (val handle = maps[mapEnumEntry]) {
            null -> {
                setUniform(mapEnumEntry.uniformKey, false)
            }
            else -> when(val texture = handle.texture) {
                null -> { }
                else -> {
                    val isDiffuse = mapEnumEntry == Material.MAP.DIFFUSE

                    when (val uploadState = handle.uploadState) {
                        UploadState.Uploaded -> {
                            bindTexture(mapEnumEntry.textureSlot, texture)
                            setUniform(mapEnumEntry.uniformKey, true)
                            if (isDiffuse) {
                                setUniform("diffuseMipBias", 0f)
                            }
                        }
                        is UploadState.Unloaded -> {
                            if (isDiffuse) {
                                if (diffuseFallbackTexture != null && uploadState.mipMapLevelToKeep == texture.mipmapCount) {
                                    bindTexture(mapEnumEntry.textureSlot, diffuseFallbackTexture)
                                    setUniform(mapEnumEntry.uniformKey, true)
                                    setUniform("diffuseMipBias", 0f)
                                } else {
                                    setUniform(mapEnumEntry.uniformKey, false)
                                    setUniform("diffuseMipBias", 0f)
                                }
                            } else {
                                setUniform(mapEnumEntry.uniformKey, false)
                            }
                        }
                        is UploadState.Uploading -> {
                            if (isDiffuse) {
                                bindTexture(mapEnumEntry.textureSlot, texture)
                                setUniform(mapEnumEntry.uniformKey, true)
                                setUniform(
                                    "diffuseMipBias",
                                    handle.currentMipMapBias
                                )
                            } else {
                                setUniform(mapEnumEntry.uniformKey, false)
                            }
                        }
                        is UploadState.MarkedForUpload -> {
                            if (isDiffuse) {
                                if (diffuseFallbackTexture != null) {
                                    bindTexture(mapEnumEntry.textureSlot, diffuseFallbackTexture)
                                    setUniform(mapEnumEntry.uniformKey, true)
                                    setUniform("diffuseMipBias", 0f)
                                } else {
                                    setUniform(mapEnumEntry.uniformKey, false)
                                    setUniform("diffuseMipBias", handle.currentMipMapBias)
                                }
                            } else {
                                setUniform(mapEnumEntry.uniformKey, false)
                            }
                        }
                    }
                }
            }
        }
    }
}