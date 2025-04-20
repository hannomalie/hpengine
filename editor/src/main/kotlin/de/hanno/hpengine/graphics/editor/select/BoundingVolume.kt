package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.graphics.editor.ImGuiEditor
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.TreeNode
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.model.BoundingVolumeComponent
import de.hanno.hpengine.model.Model
import de.hanno.hpengine.model.ModelSystem
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import imgui.ImGui
import imgui.flag.ImGuiCol
import org.koin.core.annotation.Single

data class BoundingVolumeSelection(override val entity: Int, val boundingVolumeComponent: BoundingVolumeComponent, override val components: Bag<Component>): EntitySelection {
    override fun toString(): String = boundingVolumeComponent.boundingVolume.toString()
    override fun openNextNode(currentSelection: Selection?) = when(currentSelection) {
        is BoundingVolumeComponentSelection -> currentSelection.boundingVolumeComponent == boundingVolumeComponent
        else -> false
    }
}
data class BoundingVolumeComponentSelection(override val entity: Int, val boundingVolumeComponent: BoundingVolumeComponent, override val components: Bag<Component>): EntitySelection {
    override fun toString(): String = boundingVolumeComponent.boundingVolume.toString()
}

@Single(binds = [EditorExtension::class])
class BoundingVolumeEditorExtension: EditorExtension {
    override fun getSelectionForComponentOrNull(component: Component, entity: Int, components: Bag<Component>) = when (component) {
        is BoundingVolumeComponent -> BoundingVolumeSelection(entity, component, components)
        else -> null
    }

    override fun Window.renderRightPanel(selection: Selection?) = when(selection) {
        is BoundingVolumeSelection -> selection.boundingVolumeComponent.boundingVolume
        else -> null
    }?.let { boundingVolume ->
        ImGui.text(boundingVolume.toString())
        true
    } ?: false

    override fun ImGuiEditor.renderLeftPanelComponentNode(
        component: Component,
        entity: Int,
        components: Bag<Component>,
        currentSelection: Selection?,
    ) = when(val selectionOrNull = getSelectionForComponentOrNull(component, entity, components)) {
        null -> false
        else -> {
            val componentName = component.javaClass.simpleName

            ImGui.setNextItemOpen(selectionOrNull.openNextNode(currentSelection))
            TreeNode.text(componentName) {
                selection = selectionOrNull
            }
            true
        }
    }
}