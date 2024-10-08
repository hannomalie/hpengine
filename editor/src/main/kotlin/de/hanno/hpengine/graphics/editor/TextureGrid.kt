package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.config.HighersMipMapToKeepLoaded
import de.hanno.hpengine.config.NoUnloading
import de.hanno.hpengine.config.UnloadCompletely
import de.hanno.hpengine.engine.graphics.imgui.floatInput
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.texture.*
import imgui.ImGui
import org.jetbrains.kotlin.com.intellij.psi.TypeAnnotationProvider.Static
import java.util.concurrent.TimeUnit

fun textureManagerGrid(
    config: Config,
    graphicsApi: GraphicsApi,
    textureManagerBaseSystem: TextureManagerBaseSystem
) {

    floatInput("Mip bias decrease per second", config.performance::mipBiasDecreasePerSecond, 0.1f, 20f)

    if (ImGui.button("Reload all dynamic handles")) {
        textureManagerBaseSystem.fileBasedTextures.values.filterIsInstance<DynamicHandle<*>>().forEach { texture ->
            graphicsApi.run {
                texture.uploadState = UploadState.Unloaded(null)
            }
        }
    }
    if (ImGui.button("Reload all static handles")) {
        textureManagerBaseSystem.fileBasedTextures.values.filterIsInstance<StaticHandle<*>>().forEach { texture ->
            graphicsApi.run {
                texture.uploadState = UploadState.Unloaded(null)
            }
        }
    }
    val mipLevelToKeepOrNull = when(val strategy = config.performance.textureUnloadStrategy) {
        is HighersMipMapToKeepLoaded -> strategy.level
        NoUnloading -> null
        UnloadCompletely -> null
    }
    if (ImGui.button("Reload all dynamic handles (${config.performance.textureUnloadStrategy})")) {
        textureManagerBaseSystem.fileBasedTextures.values.filterIsInstance<DynamicHandle<*>>().forEach { texture ->
            graphicsApi.run {
                texture.uploadState = UploadState.Unloaded(mipLevelToKeepOrNull)
            }
        }
    }
}