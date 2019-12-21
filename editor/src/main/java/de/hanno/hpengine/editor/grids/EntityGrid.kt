package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.entity.Entity
import net.miginfocom.swing.MigLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextField
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty0

class EntityGrid(val entity: Entity): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Name", entity::name.toTextField())
        labeled("Visible", entity::isVisible.toCheckBox())
    }

}

fun KMutableProperty<Boolean>.toCheckBox(): JCheckBox {
    return JCheckBox(name).apply {
        isSelected = this@toCheckBox.getter.call()
        addActionListener { this@toCheckBox.setter.call(isSelected) }
    }
}
fun KMutableProperty<String>.toTextField(): JTextField {
    return JTextField(name).apply {
        text = this@toTextField.getter.call()
        addActionListener { this@toTextField.setter.call(text) }
    }
}

fun JComponent.labeled(label: String, component: JComponent) {
    add(JLabel(label))
    add(component)
}


abstract class SliderInput @JvmOverloads constructor(orientation: Int,
                                                     min: Int,
                                                     max: Int,
                                                     initialValue: Int,
                                                     minorTickSpacing: Int = (max - min) / 10,
                                                     majorTickSpacing: Int = (max - min) / 4) : JPanel() {

    private var lastValue = 0

    init {
        lastValue = initialValue
        val slider = JSlider(orientation, min, max, initialValue).apply {
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
        add(slider)

    }

    abstract fun onValueChange(value: Int, delta: Int)
}

fun KMutableProperty0<Float>.toSliderInput(min: Int, max: Int): SliderInput {
    return object : SliderInput(JSlider.HORIZONTAL, min = min, max = max, initialValue = (get() * 100f).toInt()) {
        override fun onValueChange(value: Int, delta: Int) {
            set(value.toFloat() / 100f)
        }
    }
}