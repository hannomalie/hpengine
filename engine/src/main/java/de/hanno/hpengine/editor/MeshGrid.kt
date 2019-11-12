package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.model.Mesh
import net.miginfocom.swing.MigLayout
import javax.swing.JLabel
import javax.swing.JPanel

class MeshGrid(val mesh: Mesh<*>): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Name", JLabel(mesh.name))
        labeled("Material", JLabel(mesh.material.toString()))
    }

}
