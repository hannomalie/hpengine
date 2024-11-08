package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.graphics.editor.ImGuiEditor
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.imgui.dsl.TreeNode
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.model.Model
import de.hanno.hpengine.model.ModelSystem
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import imgui.ImGui
import imgui.flag.ImGuiCol
import org.koin.core.annotation.Single

data class ModelSelection(override val entity: Int, val modelComponent: ModelComponent, val model: Model<*>, override val components: Bag<Component>): EntitySelection {
    override fun toString(): String = model.file.name
    override fun openNextNode(currentSelection: Selection?) = when(currentSelection) {
        is ModelComponentSelection -> currentSelection.modelComponent == modelComponent
        is ModelSelection -> currentSelection.modelComponent == modelComponent
        is MeshSelection -> currentSelection.modelComponent == modelComponent
        else -> false
    }
}
data class ModelComponentSelection(override val entity: Int, val modelComponent: ModelComponent, override val components: Bag<Component>): EntitySelection {
    override fun toString(): String = when(val description = modelComponent.modelComponentDescription) {
        is AnimatedModelComponentDescription -> "[" + description.directory.name + "]" + description.file
        is StaticModelComponentDescription -> "[" + description.directory.name + "]" + description.file
    }
}

@Single(binds = [EditorExtension::class])
class ModelComponentEditorExtension(
    private val modelSystem: ModelSystem,
    private val textureManager: TextureManagerBaseSystem,
): EditorExtension {
    override fun getSelectionForComponentOrNull(component: Component, entity: Int, components: Bag<Component>) = when (component) {
        is ModelComponent -> ModelComponentSelection(entity, component, components)
        else -> null
    }

    override fun Window.renderRightPanel(selection: Selection?) = when(selection) {
        is ModelComponentSelection -> selection.modelComponent
        is ModelSelection -> selection.modelComponent
        else -> null
    }?.let { modelComponent ->
        modelSystem[modelComponent.modelComponentDescription]?.let { model ->
            if (ImGui.checkbox("Invert Y Texture Coord", model.isInvertTexCoordY)) {
                model.isInvertTexCoordY = !model.isInvertTexCoordY
            }
            val material = model.meshes.first().material
            materialGrid(material, textureManager)
            modelGrid(model)
        }
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
            when (component) {
                is ModelComponent -> {
                    TreeNode.treeNode("Meshes") {
                        modelSystem[component.modelComponentDescription]!!.meshes.forEach { mesh ->
                            val popNeeded = (selection as? MeshSelection)?.let {
                                if(it.mesh == mesh && it.entity == entity) {
                                    ImGui.pushStyleColor(ImGuiCol.Text, 0f, 1f, 0f, 1f)
                                    true
                                } else false
                            } ?: false
                            text(mesh.name) {
                                selection = MeshSelection(entity, mesh, component, components)
                            }
                            if(popNeeded) {
                                ImGui.popStyleColor()
                            }
                        }

                    }
                }
            }
            true
        }
    }
}