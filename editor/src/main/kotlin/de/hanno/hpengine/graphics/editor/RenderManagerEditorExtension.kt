package de.hanno.hpengine.graphics.editor

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.graphics.RenderManager
import de.hanno.hpengine.graphics.RenderSystemsConfig
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.editor.select.Selection
import de.hanno.hpengine.graphics.imgui.dsl.Window
import imgui.ImGui
import org.koin.core.annotation.Single

data class RenderManagerSelection(private val renderManager: RenderManager): Selection

@Single(binds = [EditorExtension::class])
class RenderManagerEditorExtension(
    private val primaryRendererSelection: PrimaryRendererSelection,
    private val renderSystemsConfig: RenderSystemsConfig,
    private val renderManager: RenderManager,
): EditorExtension {
    override fun ImGuiEditor.renderLeftPanelTopLevelNode() {
        Window.treeNode("RenderManager") {
            text("RenderManager") {
                selection = RenderManagerSelection(this@RenderManagerEditorExtension.renderManager)
            }
        }
    }
    override fun getSelection(any: Any, components: Bag<Component>?) = if(any is RenderManager) {
        RenderManagerSelection(any)
    } else null

    override fun Window.renderRightPanel(selection: Selection?) = when(selection) {
        is RenderManagerSelection -> {
            renderSystemsConfig.run {
                nonPrimaryRenderers.forEach {
                    if (ImGui.checkbox(it.javaClass.simpleName, it.enabled)) {
                        it.enabled = !it.enabled
                    }
                }
            }

            ImGui.text("Primary Renderer:")
            ImGui.text(renderSystemsConfig.primaryRenderer.javaClass.simpleName)
            ImGui.text("Current output")
            renderSystemsConfig.primaryRenderers.forEach {
                if (ImGui.checkbox(it.javaClass.simpleName, primaryRendererSelection.primaryRenderer == it)) {
                    primaryRendererSelection.primaryRenderer = it
                }
            }
            true
        }
        else -> false
    }

}