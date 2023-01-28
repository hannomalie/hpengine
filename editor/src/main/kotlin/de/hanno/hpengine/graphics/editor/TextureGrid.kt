package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.texture.*
import imgui.ImGui

context(ImGuiEditor, GraphicsApi)
fun textureManagerGrid(textureManagerBaseSystem: TextureManagerBaseSystem) {
    if (ImGui.button("Reload all")) {
        textureManagerBaseSystem.textures.values.filterIsInstance<FileBasedTexture2D>().forEach {
            it.uploadAsync()
        }
    }
}
context(ImGuiEditor, GraphicsApi)
fun textureGrid(key: String, texture: Texture) {
    ImGui.text(key)
    if (ImGui.beginCombo("UploadState", texture.uploadState.toString())) {
        val states = listOf(
            UploadState.Uploaded,
            UploadState.NotUploaded
        ) + (0..texture.mipmapCount).map { UploadState.Uploading(it) }

        states.forEach { type ->
            val selected = texture.uploadState == type
            if (ImGui.selectable(type.toString(), selected)) {
                texture.uploadState = type
            }
            if (selected) {
                ImGui.setItemDefaultFocus()
            }
        }
        ImGui.endCombo()
    }
    if (ImGui.button("Load")) {
        if(texture is FileBasedTexture2D) {
            texture.uploadAsync()
        }
    }

}