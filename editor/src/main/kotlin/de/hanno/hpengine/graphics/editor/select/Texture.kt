package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.config.HighersMipMapToKeepLoaded
import de.hanno.hpengine.config.NoUnloading
import de.hanno.hpengine.config.UnloadCompletely
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.texture.*
import imgui.ImGui
import org.koin.core.annotation.Single

data class TextureSelection(val path: String, val texture: Texture?): Selection
data class FileBasedTexture2DSelection(val path: String, val texture: FileBasedTexture2D): Selection


@Single(binds = [EditorExtension::class])
class TextureEditorExtension(
    private val config: Config,
    private val graphicsApi: GraphicsApi,
    private val textureManagerBaseSystem: TextureManagerBaseSystem,
): EditorExtension {
    override fun getSelectionForComponentOrNull(component: Component, entity: Int, components: Bag<Component>) = component as? TextureSelection

    override fun Window.renderRightPanel(selection: Selection?) = when (selection) {
        is TextureSelection -> {
            textureGrid(selection.path)
            true
        }
        is FileBasedTexture2DSelection -> {
            textureGrid(config, graphicsApi, selection.path, selection.texture)
            true
        }
        else -> false
    }

    private fun textureGrid(
        key: String
    ) {
        ImGui.text(key)
    }

    private fun textureGrid(
        config: Config,
        graphicsApi: GraphicsApi,
        key: String,
        fileBasedTexture2D: FileBasedTexture2D?
    ) {
        ImGui.text(key)

        val texture = fileBasedTexture2D?.texture
        if(texture != null) {
            if (ImGui.beginCombo("UploadState", fileBasedTexture2D.uploadState.toString())) {
                val states = listOf(
                    UploadState.Uploaded,
                    UploadState.Unloaded(texture.mipmapCount)
                ) + (0..< texture.mipmapCount).map { UploadState.Uploading(it) }

                states.forEach { state ->
                    val selected = fileBasedTexture2D.uploadState == state
                    if (ImGui.selectable(state.toString(), selected)) {
                        fileBasedTexture2D.uploadState = state
                    }
                    if (selected) {
                        ImGui.setItemDefaultFocus()
                    }
                }
                ImGui.endCombo()
            }
            ImGui.text("Current MipMap bias " + fileBasedTexture2D.currentMipMapBias)

            if (ImGui.button("Load")) {
                graphicsApi.run {
                    fileBasedTexture2D.uploadState = UploadState.Unloaded(null)
                    fileBasedTexture2D.uploadAsync()
                }
            }
            when(val strategy = config.performance.textureUnloadStrategy) {
                is HighersMipMapToKeepLoaded -> {
                    if (ImGui.button("Load (from mip ${strategy.level})")) {
                        (texture as? FileBasedTexture2D)?.let {
                            graphicsApi.run {
                                texture.uploadState = UploadState.Unloaded(strategy.level)
                                texture.uploadAsync()
                            }
                        }
                    }
                }
                NoUnloading -> {}
                UnloadCompletely -> {}
            }
        } else {
            ImGui.text("Currently unloaded")
        }
    }
}
