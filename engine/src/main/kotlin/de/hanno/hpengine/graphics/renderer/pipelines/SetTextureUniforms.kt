package de.hanno.hpengine.graphics.renderer.pipelines

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.deriveHandle

fun Program<*>.setTextureUniforms(
    graphicsApi: GraphicsApi,
    diffuseFallbackTexture: StaticHandle<Texture2D>? = null,
    material: Material
) = graphicsApi.run {
    for (mapEnumEntry in Material.MAP.entries) {
        val isDiffuse = mapEnumEntry == Material.MAP.DIFFUSE

        val handle = material.maps[mapEnumEntry]
        val actualHandle = if(handle == null) null else deriveHandle(
            material.maps[mapEnumEntry],
            if(isDiffuse) diffuseFallbackTexture else null
        )

        when (val texture = actualHandle?.texture) {
            null -> setUniform(mapEnumEntry.uniformKey, false)
            else -> {
                bindTexture(mapEnumEntry.textureSlot, texture)
                setUniform(mapEnumEntry.uniformKey, true)
                if(isDiffuse) {
                    setUniform("diffuseMipBias", actualHandle.currentMipMapBias)
                }
            }
        }
    }
}
