package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import net.miginfocom.swing.MigLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.reflect.KMutableProperty0

class MeshGrid(val mesh: Mesh<*>, val materialManager: MaterialManager): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Name", mesh::name.toTextField())
        labeled("Material", mesh::material.toComboBox())
    }

    fun KMutableProperty0<SimpleMaterial>.toComboBox(): JComboBox<SimpleMaterial> {
        return JComboBox(materialManager.MATERIALS.values.toTypedArray()).apply {
            addActionListener {
                this@toComboBox.set(this.selectedItem as SimpleMaterial)
            }
            selectedItem = this@toComboBox.get()
        }
    }
}

