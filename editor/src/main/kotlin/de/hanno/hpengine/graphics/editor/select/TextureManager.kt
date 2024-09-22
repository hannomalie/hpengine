package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.editor.ImGuiEditor
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.editor.textureManagerGrid
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import org.koin.core.annotation.Single

data class TextureManagerSelection(val textureManagerBaseSystem: TextureManagerBaseSystem): Selection

@Single(binds = [EditorExtension::class])
class TextureManagerEditorExtension(
    private val config: Config,
    private val graphicsApi: GraphicsApi,
    private val textureManagerBaseSystem: TextureManagerBaseSystem,
): EditorExtension {
    override fun ImGuiEditor.renderLeftPanelTopLevelNode() {
        Window.treeNode("Textures") {
            text("Manager") {
                selection = TextureManagerSelection(textureManagerBaseSystem)
            }
            treeNode("Textures") {
                textureManagerBaseSystem.textures.entries.sortedBy { it.key }.forEach { (key, texture) ->
                    text(key.takeLast(15)) {
                        selection = TextureSelection(key, texture)
                    }
                }
            }
        }
    }
    override fun getSelection(any: Any, components: Bag<Component>?): Selection? = if(any is TextureManagerBaseSystem) {
        TextureManagerSelection(any)
    } else null

    override fun Window.renderRightPanel(selection: Selection?) = if(selection is TextureManagerSelection) {
        textureManagerGrid(config, graphicsApi, selection.textureManagerBaseSystem)
        true
    } else {
        false
    }
}