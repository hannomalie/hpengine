package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.texture.*
import imgui.ImGui

fun textureManagerGrid(
    graphicsApi: GraphicsApi,
    textureManagerBaseSystem: TextureManagerBaseSystem
) {
    if (ImGui.button("Reload all")) {
        textureManagerBaseSystem.textures.values.filterIsInstance<FileBasedTexture2D<Texture2D>>().forEach { texture ->
            graphicsApi.run { texture.uploadAsync() }
        }
    }
}