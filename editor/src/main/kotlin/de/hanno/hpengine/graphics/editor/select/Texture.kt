package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.texture.*
import imgui.ImGui
import org.koin.core.annotation.Single

data class TextureSelection(val path: String, val texture: Texture): Selection


@Single(binds = [EditorExtension::class])
class OceanWaterEditorExtension(
    private val graphicsApi: GraphicsApi,
    private val textureManagerBaseSystem: TextureManagerBaseSystem,
): EditorExtension {
    override fun getSelectionForComponentOrNull(component: Component, entity: Int, components: Bag<Component>) = component as? TextureSelection

    override fun Window.renderRightPanel(selection: Selection?) = if(selection is TextureSelection) {
        textureGrid(graphicsApi, selection.path, selection.texture)
        true
    } else {
        false
    }
}

private fun textureGrid(
    graphicsApi: GraphicsApi,
    key: String,
    texture: Texture
) {
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
        (texture as? FileBasedTexture2D<Texture2D>)?.let {
            graphicsApi.run { texture.uploadAsync() }
        }
    }

}