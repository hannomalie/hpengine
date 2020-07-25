package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.ExtensionState
import de.hanno.hpengine.engine.model.AnimatedMesh
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.StaticMesh
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.transform.AABBData
import net.miginfocom.swing.MigLayout
import org.apache.xpath.operations.Bool
import org.joml.Vector3f
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.reflect.KMutableProperty0


class MeshGrid(val mesh: Mesh<*>, val entity: Entity, val materialManager: MaterialManager): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Name", mesh::name.toTextField())
        labeled("Material", mesh::material.toComboBox())
        labeled("Unique Vertices", JLabel(mesh.vertices.size.toString()))
        labeled("", JButton("Reset AABB").apply {
            addActionListener {
                val newAABB = when(mesh) {
                    is StaticMesh -> mesh.calculateMinMax(entity.transform)
                    is AnimatedMesh -> mesh.calculateMinMax(entity.transform)
                    else -> throw IllegalStateException("Something else than the known meshes found")
                }
                mesh.spatial.minMax.localAABB = newAABB
            }
        })
        mesh.spatial.minMax.toInputs().forEach { (label, component) ->
            labeled(label, component)
        }
    }

    fun KMutableProperty0<Material>.toComboBox(): JComboBox<SimpleMaterial> {
        return JComboBox(materialManager.MATERIALS.values.toTypedArray()).apply {
            addActionListener {
                this@toComboBox.set(this.selectedItem as SimpleMaterial)
            }
            selectedItem = this@toComboBox.get()
        }
    }
}

