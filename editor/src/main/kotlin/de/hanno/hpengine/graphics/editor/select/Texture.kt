package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.model.material.deriveHandle
import imgui.ImGui
import org.koin.core.annotation.Single
import java.util.concurrent.TimeUnit

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
        handle: FileBasedTexture2D
    ) {
        ImGui.text(key)
        ImGui.text("Can be unloaded: " + textureManagerBaseSystem.run {
            handle.canBeUnloaded
        })
        graphicsApi.getHandleUsageTimeStamp(handle.handle)?.let { usageTimeStamp ->
            ImGui.text("Usage:")
            val notUsedForNanos = System.nanoTime() - usageTimeStamp
            val ms = TimeUnit.NANOSECONDS.toMillis(notUsedForNanos).toFloat()
            ImGui.text("Not used for $ms ms")
        }

        when(handle) {
            is DynamicFileBasedTexture2D -> {
                ImGui.text("-> dynamic handle")
            }
            is StaticFileBasedTexture2D -> {
                ImGui.text("-> static handle")
            }
        }

        val texture = handle.texture
        if (texture == null) {
            ImGui.text("Currently unloaded")
        } else {
            ImGui.text("Filter config: " + texture.textureFilterConfig)
            ImGui.text("Image count: " + texture.imageCount)
            if (ImGui.beginCombo("UploadState", handle.uploadState.toString())) {
                val states = listOf(
                    UploadState.Uploaded,
                    UploadState.Unloaded,
                    UploadState.ForceFallback,
                ) + (0..< texture.mipMapCount).map { UploadState.Uploading(it) }

                states.forEach { state ->
                    val selected = handle.uploadState == state
                    if (ImGui.selectable(state.toString(), selected)) {
                        handle.uploadState = state
                    }
                    if (selected) {
                        ImGui.setItemDefaultFocus()
                    }
                }
                ImGui.endCombo()
            }
            ImGui.text("Current MipMap bias " + handle.currentMipMapBias)

            if (ImGui.button("Load")) {
                graphicsApi.run {
                    handle.uploadState = UploadState.Unloaded
                    handle.uploadAsync()
                }
            }
        }

        when(handle) {
            is DynamicFileBasedTexture2D -> {
                when(val fallback = handle.fallback) {
                    null -> {}
                    else -> {
                        val derivesFallback = deriveHandle(handle.handle, handle) == fallback
                        ImGui.text("Fallback will be used: $derivesFallback")
                        ImGui.text("Fallback uploadstate ${fallback.uploadState}")
                        ImGui.text("Fallback image count ${fallback.texture.imageCount}")
                        ImGui.text("Fallback mipmap bias ${fallback.currentMipMapBias}")
                    }
                }
            }
            is StaticFileBasedTexture2D -> { }
        }
    }
}
