package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.Window
import imgui.ImGui
import org.koin.core.annotation.Single

data class TransformSelection(
    override val entity: Int, val transform: TransformComponent,
    override val components: Bag<Component>
): EntitySelection {
    override fun toString(): String = entity.toString()
}

@Single(binds = [EditorExtension::class])
class TransformEditorExtension : EditorExtension {
    override fun getSelectionForComponentOrNull(component: Component, entity: Int, components: Bag<Component>) = if(component is TransformComponent) {
            TransformSelection(entity, component, components)
        } else null

    override fun Window.renderRightPanel(selection: Selection?) = if(selection is TransformSelection) {
        if(ImGui.button("Reset transform")) {
            selection.transform.transform.identity()
        }
        true
    } else {
        false
    }
}