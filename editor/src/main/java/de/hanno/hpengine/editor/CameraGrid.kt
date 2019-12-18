package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.camera.Camera
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel

class CameraGrid(val camera: Camera): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Entity", camera.entity::name.toTextField())
        labeled("Exposure", camera::exposure.toSliderInput(0, 10*100))
        labeled("Field of view", camera::fov.toSliderInput(30*100, 120*100))
        labeled("FocalDepth", camera::focalDepth.toSliderInput(0, 150*100))
        labeled("FocalLength", camera::focalLength.toSliderInput(15*100, 200*100))
        labeled("fStop", camera::fStop.toSliderInput(0, 20*100))
    }
}
