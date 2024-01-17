package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.graphics.editor.ImGuiEditor
import de.hanno.hpengine.graphics.editor.OutputSelection
import de.hanno.hpengine.graphics.editor.RenderManagerSelection
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.Window
import org.koin.core.annotation.Single

data class OutputSelectionSelection(val outputSelection: OutputSelection): Selection


@Single(binds = [EditorExtension::class])
class OutputSelectionEditorExtension(
    private val outputSelection: OutputSelection,
): EditorExtension {
    override fun getSelection(any: Any, components: Bag<Component>?): Selection? = when (any) {
        is OutputSelection -> OutputSelectionSelection(any)
        else -> null
    }

    override fun ImGuiEditor.renderLeftPanelTopLevelNode() {
        Window.treeNode("Render Output") {
            text("Output") {
                selection = OutputSelectionSelection(this@OutputSelectionEditorExtension.outputSelection)
            }
        }
    }
    override fun Window.renderRightPanel(selection: Selection?): Boolean = when (selection) {
        is OutputSelectionSelection -> {
            selection.outputSelection.renderSelection()
            true
        }
        else -> false
    }
}