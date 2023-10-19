package de.hanno.hpengine.graphics.editor.extension

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.graphics.editor.ImGuiEditor
import de.hanno.hpengine.graphics.editor.select.Selection
import de.hanno.hpengine.graphics.imgui.dsl.TreeNode
import de.hanno.hpengine.graphics.imgui.dsl.Window
import imgui.ImGui

interface EditorExtension {
    fun ImGuiEditor.renderLeftPanelTopLevelNode() {}
    fun getSelection(any: Any, components: Bag<Component>?): Selection? = null
    fun getSelectionForComponentOrNull(component: Component, entity: Int, components: Bag<Component>): Selection? = null

    fun Window.renderRightPanel(selection: Selection?): Boolean
    fun ImGuiEditor.renderLeftPanelComponentNode(component: Component, entity: Int, components: Bag<Component>, currentSelection: Selection?): Boolean {
        return when(val selectionOrNull = getSelectionForComponentOrNull(component, entity, components)) {
            null -> false
            else -> {
                val componentName = component.javaClass.simpleName

                ImGui.setNextItemOpen(selectionOrNull.openNextNode(currentSelection))
                TreeNode.text(componentName) {
                    selectOrUnselect(selectionOrNull)
                }
                true
            }
        }
    }
}