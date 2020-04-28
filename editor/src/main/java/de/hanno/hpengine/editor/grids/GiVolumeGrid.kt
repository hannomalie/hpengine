package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.GIVolumeComponent
import net.miginfocom.swing.MigLayout
import javax.swing.JButton
import javax.swing.JPanel

class GiVolumeGrid(val giVolumeComponent: GIVolumeComponent, val engine: Engine<OpenGl>): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        add(JButton("Use cam").apply { addActionListener {
            engine.sceneManager.scene.activeCamera = giVolumeComponent.orthoCam
        }
        })
        add(JButton("Restore scene cam").apply { addActionListener {
            engine.sceneManager.scene.activeCamera = engine.sceneManager.scene.camera
        }
        })
    }

}