package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.model.AnimatedMesh
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.StaticMesh
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import net.miginfocom.swing.MigLayout
import javax.swing.JButton
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
                    is StaticMesh -> mesh.calculateAABB(entity.transform)
                    is AnimatedMesh -> mesh.calculateAABB(entity.transform)
                    else -> throw IllegalStateException("Something else than the known meshes found")
                }
                mesh.spatial.boundingVolume.localAABB = newAABB
            }
        })
        mesh.spatial.boundingVolume.toInputs().forEach { (label, component) ->
            labeled(label, component)
        }
    }

    fun KMutableProperty0<Material>.toComboBox(): JComboBox<Material> {
        return JComboBox(materialManager.materials.toTypedArray()).apply {
            addActionListener {
                this@toComboBox.set(this.selectedItem as Material)
            }
            selectedItem = this@toComboBox.get()
        }
    }
}

