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
            handle,
            if(isDiffuse) diffuseFallbackTexture else null
        )

        when (actualHandle) {
            null -> setUniform(mapEnumEntry.uniformKey, false)
            else -> {
                bindHandle(mapEnumEntry.textureSlot, actualHandle)
//                bindTexture(mapEnumEntry.textureSlot, actualHandle.texture!!)
                handle?.let {
                    setHandleUsageTimeStamp(it)
                }
                setUniform(mapEnumEntry.uniformKey, true)
                if(isDiffuse) {
                    setUniform("diffuseMipBias", actualHandle.currentMipMapBias)
                }
            }
        }
    }
}
