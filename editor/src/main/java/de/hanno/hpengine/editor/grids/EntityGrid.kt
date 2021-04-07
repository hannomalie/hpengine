package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.model.Update
import net.miginfocom.swing.MigLayout
import java.util.Hashtable
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


abstract class DecimalSliderInput @JvmOverloads constructor(orientation: Int,
                                                            min: Float,
                                                            max: Float,
                                                            initialValue: Float,
                                                            majorTickSpacing: Float = (max - min) / 4) : JPanel() {

    private var lastValue = 0

    init {
        lastValue = initialValue.scaleToInt()
        val slider = JSlider(orientation, min.scaleToInt(), max.scaleToInt(), initialValue.scaleToInt()).apply {
            paintTicks = true
            paintLabels = true

            this.majorTickSpacing = majorTickSpacing.scaleToInt()

            val labelsMajor = (0 .. 4).map { it * (max.scaleToInt() / 4) }.associateWith { JLabel("${it.scaleToFloat()}") }
            labelTable = Hashtable(labelsMajor)

            addChangeListener { e ->
                val delta = value - lastValue

                onValueChange(value, delta)

                lastValue = value
            }
        }
        add(slider)

    }

    fun Float.scaleToInt() = (this * internalFactor).toInt()
    fun Int.scaleToFloat() = toFloat() / internalFactor.toFloat()

    abstract fun onValueChange(value: Int, delta: Int)

    companion object {
        val internalFactor: Int = 1000
    }
}

fun KMutableProperty0<Float>.toSliderInput(min: Float, max: Float): DecimalSliderInput = object : DecimalSliderInput(JSlider.HORIZONTAL, min = min, max = max, initialValue = get()) {
    override fun onValueChange(value: Int, delta: Int) {
        set(value.scaleToFloat())
    }
}

fun KMutableProperty0<Int>.toSliderInput(min: Int, max: Int): DecimalSliderInput = object : DecimalSliderInput(JSlider.HORIZONTAL, min = min.toFloat(), max = max.toFloat(), initialValue = get().toFloat()) {
    override fun onValueChange(value: Int, delta: Int) {
        set(value.scaleToFloat().toInt())
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