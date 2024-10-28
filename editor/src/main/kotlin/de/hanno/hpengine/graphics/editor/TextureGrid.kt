package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.config.NoUnloading
import de.hanno.hpengine.config.Unloading
import de.hanno.hpengine.engine.graphics.imgui.floatInput
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.texture.DynamicHandle
import de.hanno.hpengine.graphics.texture.StaticHandle
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.graphics.texture.UploadState
import imgui.ImGui

fun textureManagerGrid(
    config: Config,
    graphicsApi: GraphicsApi,
    textureManagerBaseSystem: TextureManagerBaseSystem
) {
    floatInput("Mip bias decrease per second", config.performance::mipBiasDecreasePerSecond, 0.1f, 20f)

    floatInput("Unload bias in seconds", config.performance::unloadBiasInSeconds, 0.1f, 20f)
    floatInput("Unload distance", config.performance::unloadDistance, 1f, 300f)

    if (ImGui.beginCombo("Texture unload strategy", config.performance.textureUnloadStrategy.toString())) {
        (listOf(NoUnloading) + (0..<6).map { Unloading(it) }).forEach {
            val selected = config.performance.textureUnloadStrategy == it
            if (ImGui.selectable(it.toString(), selected)) {
                config.performance.textureUnloadStrategy = it
            }
            if (selected) {
                ImGui.setItemDefaultFocus()
            }
        }
        ImGui.endCombo()
    }
    if (ImGui.button("Reload all dynamic handles")) {
        textureManagerBaseSystem.fileBasedTextures.values.filterIsInstance<DynamicHandle<*>>().forEach { texture ->
            graphicsApi.run {
                texture.uploadState = UploadState.Unloaded
            }
        }
    }
    if (ImGui.button("Reload all static handles")) {
        textureManagerBaseSystem.fileBasedTextures.values.filterIsInstance<StaticHandle<*>>().forEach { texture ->
            graphicsApi.run {
                texture.uploadState = UploadState.Unloaded
            }
        }
    }
    if (ImGui.button("Reload all dynamic handles (${config.performance.textureUnloadStrategy})")) {
        textureManagerBaseSystem.fileBasedTextures.values.filterIsInstance<DynamicHandle<*>>().forEach { texture ->
            graphicsApi.run {
                texture.uploadState = UploadState.Unloaded
            }
        }
    }

    ImGui.text("Texture Pool:")
    textureManagerBaseSystem.texturePool.forEach {
        ImGui.text(it.path)
    }
}