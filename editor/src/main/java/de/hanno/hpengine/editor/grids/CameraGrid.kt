package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.camera.Camera
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel

class CameraGrid(val camera: Camera): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Entity", camera.entity::name.toTextField())
        labeled("Exposure", camera::exposure.toSliderInput(0f, 10f))
        labeled("Field of view", camera::fov.toSliderInput(30f, 120f))
        labeled("FocalDepth", camera::focalDepth.toSliderInput(0f, 150f))
        labeled("FocalLength", camera::focalLength.toSliderInput(15f, 200f))
        labeled("fStop", camera::fStop.toSliderInput(0f, 20f))
    }
}
