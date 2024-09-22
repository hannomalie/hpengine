package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.texture.*
import imgui.ImGui

fun textureManagerGrid(
    config: Config,
    graphicsApi: GraphicsApi,
    textureManagerBaseSystem: TextureManagerBaseSystem
) {
    if (ImGui.button("Reload all")) {
        textureManagerBaseSystem.textures.values.filterIsInstance<FileBasedTexture2D<Texture2D>>().forEach { texture ->
            graphicsApi.run { texture.uploadState = UploadState.Unloaded(texture.mipmapCount-1) }
        }
    }
    if (ImGui.button("Reload all (from mip ${config.performance.maxMipMapToKeepLoaded})")) {
        textureManagerBaseSystem.textures.values.filterIsInstance<FileBasedTexture2D<Texture2D>>().forEach { texture ->
            graphicsApi.run { texture.uploadState = UploadState.Unloaded(config.performance.maxMipMapToKeepLoaded) }
        }
    }
}