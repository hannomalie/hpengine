package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.scene.OceanWaterExtension
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel

class OceanWaterGrid(val oceanWater: OceanWaterExtension.OceanWater) : JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Amplitude", oceanWater::amplitude.toSliderInput(0f, 20f))
        labeled("Windspeed", oceanWater::windspeed.toSliderInput(0f, 100f))
        labeled("Time factor", oceanWater::timeFactor.toSliderInput(0.1f, 10f))
        labeled("Direction X", oceanWater.direction::myX.toSliderInput(-1f, 1f))
        labeled("Direction Y", oceanWater.direction::myY.toSliderInput(-1f, 1f))
        labeled("L", oceanWater::L.toSliderInput(100, 2000))
    }
}
