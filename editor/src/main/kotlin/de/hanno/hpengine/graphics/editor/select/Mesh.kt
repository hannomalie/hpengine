package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.model.Mesh
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.visibility.InvisibleComponentSystem
import imgui.ImGui
import org.koin.core.annotation.Single


data class MeshSelection(override val entity: Int, val mesh: Mesh<*>, val modelComponent: ModelComponent, override val components: Bag<Component>): EntitySelection {
    override fun toString(): String = mesh.name
}

@Single(binds = [EditorExtension::class])
class MeshEditorExtension(
    private val invisibleComponentSystem: InvisibleComponentSystem,
    private val materialSystem: MaterialSystem,
) : EditorExtension {

    override fun Window.renderRightPanel(selection: Selection?) = if(selection is MeshSelection) {
        val components = selection.components
        val entity = selection.entity

        entityInputs(components, invisibleComponentSystem, entity)
        text(selection.mesh.name)

        if (ImGui.beginCombo("Material", selection.mesh.material.name)) {
            materialSystem.materials.distinctBy { it.name }.forEach { material ->
                val selected = selection.mesh.material.name == material.name
                if (ImGui.selectable(material.name, selected)) {
                    selection.mesh.material = material
                }
                if (selected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }
        true
    } else {
        false
    }
}