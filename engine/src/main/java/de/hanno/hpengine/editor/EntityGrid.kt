package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.entity.Entity
import net.miginfocom.swing.MigLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.reflect.KMutableProperty

class EntityGrid(val entity: Entity): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Name", entity::name.toTextField())
        labeled("Visible", entity::isVisible.toCheckBox())
    }

    private fun KMutableProperty<Boolean>.toCheckBox(): JCheckBox {
        return JCheckBox(name).apply {
            isSelected = this@toCheckBox.getter.call()
            addActionListener { this@toCheckBox.setter.call(isSelected) }
        }
    }
    private fun KMutableProperty<String>.toTextField(): JTextField {
        return JTextField(name).apply {
            text = this@toTextField.getter.call()
            addActionListener { this@toTextField.setter.call(text) }
        }
    }

    private fun labeled(label: String, component: JComponent) {
        add(JLabel(label))
        add(component)
    }
}