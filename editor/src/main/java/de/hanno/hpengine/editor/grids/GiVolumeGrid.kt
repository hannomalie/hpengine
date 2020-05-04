package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.GIVolumeComponent
import net.miginfocom.swing.MigLayout
import org.joml.Vector3f
import java.beans.PropertyChangeListener
import java.text.NumberFormat
import javax.swing.JButton
import javax.swing.JFormattedTextField
import javax.swing.JPanel
import javax.swing.JSlider
import kotlin.reflect.KMutableProperty0

class GiVolumeGrid(val giVolumeComponent: GIVolumeComponent, val engine: Engine<OpenGl>) : JPanel() {
    init {
        layout = MigLayout("wrap 2")
        add(JButton("Use cam").apply {
            addActionListener {
                engine.sceneManager.scene.activeCamera = giVolumeComponent.orthoCam
            }
        })
        add(JButton("Restore scene cam").apply {
            addActionListener {
                engine.sceneManager.scene.activeCamera = engine.sceneManager.scene.camera
            }
        })
        labeled("Min", giVolumeComponent.minMax.min.toInput())
        labeled("Max", giVolumeComponent.minMax.max.toInput())
    }

}

private fun Vector3f.toInput(): JPanel {
    val xInput = JFormattedTextField(this@toInput.x).apply {
        columns = 5
        addPropertyChangeListener("value") { this@toInput.x = it.newValue as Float }
    }
    val yInput = JFormattedTextField(this@toInput.y).apply {
        columns = 5
        addPropertyChangeListener("value") { this@toInput.y = it.newValue as Float }
    }
    val zInput = JFormattedTextField(this@toInput.z).apply {
        columns = 5
        addPropertyChangeListener("value") { this@toInput.z = it.newValue as Float }
    }
    return JPanel().apply {
        labeled("X", xInput)
        labeled("Y", yInput)
        labeled("Z", zInput)
    }
}

