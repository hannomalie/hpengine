package de.hanno.hpengine.graphics.imgui.editor

import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.graphics.texture.FileBasedTexture2D
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import imgui.ImGui

context(ImGuiEditor)
fun textureSelection(material: Material) {
    ImGui.text("Textures")
    val all2DTextures = artemisWorld.getSystem(OpenGLTextureManager::class.java)
        .textures.filterValues { it is FileBasedTexture2D } + textureManager.texturesForDebugOutput

    val all2DTexturesNames = all2DTextures.keys.toTypedArray()

    Material.MAP.values().forEach { type ->
        material.maps[type].let { currentTexture ->
            val currentIndex = all2DTextures.values.indexOf(currentTexture)
            val is2DTexture = currentIndex > -1
            val previewValue = when {
                is2DTexture -> all2DTexturesNames[currentIndex]
                currentTexture != null -> "Other"
                else -> "None"
            }
            if (ImGui.beginCombo(type.name, previewValue)) {
                if (ImGui.selectable("None", currentTexture == null)) {
                    material.maps.remove(type)
                }
                all2DTextures.forEach { (name, texture) ->
                    val selected = currentTexture == texture
                    if (ImGui.selectable(name, selected)) {
                        material.maps[type] = texture
                    }
                    if (selected) {
                        ImGui.setItemDefaultFocus()
                    }
                }

                ImGui.endCombo()
            }
        }
    }
}