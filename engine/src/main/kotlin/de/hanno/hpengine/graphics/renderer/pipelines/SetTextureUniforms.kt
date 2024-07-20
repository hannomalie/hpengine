package de.hanno.hpengine.graphics.renderer.pipelines

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.texture.Texture
import de.hanno.hpengine.graphics.texture.UploadState
import de.hanno.hpengine.model.material.Material

fun Program<*>.setTextureUniforms(
    graphicsApi: GraphicsApi,
    maps: Map<Material.MAP, Texture>,
    diffuseFallbackTexture: Texture? = null
) = graphicsApi.run {
    for (mapEnumEntry in Material.MAP.entries) {
        if (maps.contains(mapEnumEntry)) {
            val map = maps[mapEnumEntry]!!
            if (map.id > 0) {
                val isDiffuse = mapEnumEntry == Material.MAP.DIFFUSE

                when (map.uploadState) {
                    UploadState.Uploaded -> {
                        bindTexture(mapEnumEntry.textureSlot, map)
                        setUniform(mapEnumEntry.uniformKey, true)
                        if (isDiffuse) {
                            setUniform("diffuseMipBias", 0)
                        }
                    }

                    UploadState.NotUploaded -> {
                        if (isDiffuse) {
                            if (diffuseFallbackTexture != null) {
                                bindTexture(mapEnumEntry.textureSlot, diffuseFallbackTexture)
                                setUniform(mapEnumEntry.uniformKey, true)
                                setUniform("diffuseMipBias", 0)
                            } else {
                                setUniform(mapEnumEntry.uniformKey, false)
                                setUniform("diffuseMipBias", 0)
                            }
                        } else {
                            setUniform(mapEnumEntry.uniformKey, false)
                        }
                    }

                    is UploadState.Uploading -> {
                        if (isDiffuse) {
                            bindTexture(mapEnumEntry.textureSlot, map)
                            setUniform(mapEnumEntry.uniformKey, true)
                            setUniform(
                                "diffuseMipBias",
                                (map.uploadState as UploadState.Uploading).maxMipMapLoaded
                            )
                        } else {
                            setUniform(mapEnumEntry.uniformKey, false)
                        }
                    }
                }
            }
        } else {
            setUniform(mapEnumEntry.uniformKey, false)
        }
    }
}