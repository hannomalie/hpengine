package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.engine.graphics.imgui.floatInput
import de.hanno.hpengine.model.material.Material
import imgui.ImGui

fun ImGuiEditor.materialGrid(material: Material) {
    ImGui.text(material.name)
    val colors = floatArrayOf(
        material.diffuse.x,
        material.diffuse.y,
        material.diffuse.z
    )
    if (ImGui.colorPicker3("Albedo", colors)) {
        material.diffuse.x = colors[0]
        material.diffuse.y = colors[1]
        material.diffuse.z = colors[2]
    }
    floatInput("Roughness", material.roughness) { floatArray -> material.roughness = floatArray[0] }
    floatInput("Metallic", material.metallic) { floatArray -> material.metallic = floatArray[0] }
    floatInput("Ambient", material.ambient) { floatArray -> material.ambient = floatArray[0] }
    floatInput("Transparency", material.transparency) { floatArray ->
        material.transparency = floatArray[0]
    }
    floatInput("ParallaxScale", material.parallaxScale) { floatArray ->
        material.parallaxScale = floatArray[0]
    }
    floatInput("ParallaxBias", material.parallaxBias) { floatArray ->
        material.parallaxBias = floatArray[0]
    }
    floatInput("UVScaleX", material.uvScale.x, 0.01f, 10f) { floatArray ->
        material.uvScale.x = floatArray[0]
    }
    floatInput("UVScaleY", material.uvScale.y, 0.01f, 10f) { floatArray ->
        material.uvScale.y = floatArray[0]
    }
    floatInput("LODFactor", material.lodFactor, 1f, 100f) { floatArray -> material.lodFactor = floatArray[0] }
    if (ImGui.checkbox("WorldSpaceTexCoords", material.useWorldSpaceXZAsTexCoords)) {
        material.useWorldSpaceXZAsTexCoords = !material.useWorldSpaceXZAsTexCoords
    }
    if (ImGui.beginCombo("Type", material.materialType.toString())) {
        Material.MaterialType.values().forEach { type ->
            val selected = material.materialType == type
            if (ImGui.selectable(type.toString(), selected)) {
                material.materialType = type
            }
            if (selected) {
                ImGui.setItemDefaultFocus()
            }
        }
        ImGui.endCombo()
    }
    if (ImGui.beginCombo("TransparencyType", material.transparencyType.toString())) {
        Material.TransparencyType.values().forEach { type ->
            val selected = material.transparencyType == type
            if (ImGui.selectable(type.toString(), selected)) {
                material.transparencyType = type
            }
            if (selected) {
                ImGui.setItemDefaultFocus()
            }
        }
        ImGui.endCombo()
    }
    if (ImGui.checkbox("BackFaceCulling", material.cullBackFaces)) {
        material.cullBackFaces = !material.cullBackFaces
    }
    if (ImGui.checkbox("DepthTest", material.depthTest)) {
        material.depthTest = !material.depthTest
    }
    if (ImGui.beginCombo("EnvironmentMapType", material.environmentMapType.toString())) {
        Material.ENVIRONMENTMAP_TYPE.values().forEach { type ->
            val selected = material.environmentMapType == type
            if (ImGui.selectable(type.toString(), selected)) {
                material.environmentMapType = type
            }
            if (selected) {
                ImGui.setItemDefaultFocus()
            }
        }
        ImGui.endCombo()
    }
    if (ImGui.checkbox("CastShadows", material.isShadowCasting)) {
        material.isShadowCasting = !material.isShadowCasting
    }
    textureSelection(this, material)
}