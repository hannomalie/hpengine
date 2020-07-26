package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.scene.Scene
import net.miginfocom.swing.MigLayout
import javax.swing.JLabel
import javax.swing.JPanel

class SceneGrid(val scene: Scene): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Name", JLabel(scene.name))
        labeled("Min", JLabel(scene.aabb.min.toString()))
        labeled("Max", JLabel(scene.aabb.max.toString()))
    }
}
