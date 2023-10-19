package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.model.material.Material
import imgui.ImGui

fun textureSelection(
    material: Material,
    textureManager: TextureManagerBaseSystem
) {
    ImGui.text("Textures")
    val all2DTextures = textureManager
        .textures.filterValues { it is Texture2D } + textureManager.texturesForDebugOutput

    val all3DTextures = textureManager
        .textures.filterValues { it is CubeMap }

    val all2DTexturesNames = all2DTextures.keys.toTypedArray()
    val all3DTexturesNames = all3DTextures.keys.toTypedArray()

    Material.MAP.values().forEach { type ->
        if(type == Material.MAP.ENVIRONMENT) {
            material.maps[type].let { currentTexture ->
                val currentIndex = all3DTextures.values.indexOf(currentTexture)
                val is3DTexture = currentIndex > -1
                val previewValue = when {
                    is3DTexture -> all3DTexturesNames[currentIndex]
                    currentTexture != null -> "Other"
                    else -> "None"
                }
                if (ImGui.beginCombo(type.name, previewValue)) {
                    if (ImGui.selectable("None", currentTexture == null)) {
                        material.maps.remove(type)
                    }
                    all3DTextures.forEach { (name, texture) ->
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
        } else {
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
}