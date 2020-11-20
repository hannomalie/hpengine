package de.hanno.hpengine.editor.grids

import com.sun.java.accessibility.util.SwingEventMonitor.addChangeListener
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.model.Update
import net.miginfocom.swing.MigLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty0

class EntityGrid(val entity: Entity): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Name", entity::name.toTextField())
        labeled("Visible", entity::visible.toCheckBox())
        labeled("Update", entity::updateType.toComboBox(Update.values()))
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

fun KMutableProperty0<Float>.toSliderInput(min: Int, max: Int): SliderInput = object : SliderInput(JSlider.HORIZONTAL, min = min, max = max, initialValue = (get() * 100f).toInt()) {
    override fun onValueChange(value: Int, delta: Int) {
        set(value.toFloat() / 100f)
    }
}

fun KMutableProperty0<Float>.toTextInput(): JTextField = JTextField(get().toString()).apply {
    document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) { onChange() }
        override fun removeUpdate(e: DocumentEvent) { onChange() }
        override fun changedUpdate(e: DocumentEvent) { onChange() }
        fun onChange() {
            set(document.getText(0, document.length-1).toFloat())
        }
    })
}