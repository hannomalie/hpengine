package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.graphics.light.point.PointLight
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel

class PointLightGrid(pointLight: PointLight): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Radius", pointLight::radius.toSliderInput(0.1f, 100f))
        labeled("Rendered Sphere", pointLight::renderedSphereRadius.toSliderInput(0.1f, 100f))
        labeled("Color", pointLight::color.toColorPickerInput())
    }

}
