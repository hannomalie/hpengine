package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.visibility.InvisibleComponentSystem
import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.koin.core.annotation.Single

data class SimpleEntitySelection(override val entity: Int, override val components: Bag<Component>): EntitySelection {
    override fun toString(): String = entity.toString()
    override fun openNextNode(currentSelection: Selection?) = (currentSelection as? SimpleEntitySelection)?.entity == entity
}

@Single(binds = [EditorExtension::class])
class EntityEditorExtension(
    private val invisibleComponentSystem: InvisibleComponentSystem
) : EditorExtension {
    override fun Window.renderRightPanel(selection: Selection?) = if(selection is SimpleEntitySelection) {
        entityInputs(selection.components, invisibleComponentSystem, selection.entity)
        true
    } else {
        false
    }
}

fun Window.entityInputs(
    components: Bag<Component>,
    system: InvisibleComponentSystem,
    entity: Int
) {
    components.firstIsInstanceOrNull<NameComponent>()?.run {
        text("Name: $name")
    }
    checkBox("Visible", !system.invisibleComponentMapper.has(entity)) { visible ->
        system.invisibleComponentMapper.set(entity, !visible)
    }
    components.firstIsInstanceOrNull<TransformComponent>()?.run {
        val position = transform.position
        val positionArray = floatArrayOf(position.x, position.y, position.z)
        ImGui.inputFloat3("Position", positionArray, "%.3f", ImGuiInputTextFlags.ReadOnly)
    }
}