package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel

class DirectionalLightGrid(directionalLight: DirectionalLight): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Radius", directionalLight::scatterFactor.toSliderInput(1, 100))
        labeled("Color", directionalLight::color.toColorPickerInput())
    }

}