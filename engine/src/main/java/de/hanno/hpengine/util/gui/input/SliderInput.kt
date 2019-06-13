package de.hanno.hpengine.util.gui.input

import com.alee.extended.panel.WebTitledPanel
import com.alee.laf.label.WebLabel
import com.alee.laf.slider.WebSlider

abstract class SliderInput @JvmOverloads constructor(labelString: String,
                           orientation: Int,
                           min: Int,
                           max: Int,
                           initialValue: Int,
                           minorTickSpacing: Int = (max - min) / 10,
                           majorTickSpacing: Int = (max-min) / 4) : WebTitledPanel() {

    private var lastValue = 0

    init {
        title = WebLabel(labelString)
        lastValue = initialValue
        val slider = WebSlider(orientation, min, max, initialValue).apply {
            paintTicks = true
            paintLabels = true

            this.minorTickSpacing = minorTickSpacing
            this.majorTickSpacing = majorTickSpacing

            addChangeListener { e ->
                val delta = value - lastValue

                onValueChange(value, delta)

                lastValue = value
            }
        }
        content = slider

    }

    abstract fun onValueChange(value: Int, delta: Int)
}
