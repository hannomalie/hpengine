package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.config.DebugConfig
import de.hanno.hpengine.engine.config.SimpleConfig
import net.miginfocom.swing.MigLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaType

class ConfigGrid(val config: SimpleConfig): JPanel() {
    init {
        layout = MigLayout("wrap 4")
        DebugConfig::class.declaredMemberProperties.forEach {
            if(it.returnType.javaType == Boolean::class.java ) {
                val checkBox = when {
                    it is KMutableProperty1<*, *> -> (it as? KMutableProperty1<DebugConfig, Boolean>)?.toCheckBox(config.debug)
                    else -> (it as? KProperty1<DebugConfig, Boolean>)?.toCheckBox(config.debug)
                }
                if(checkBox != null) add(checkBox)
            }
        }
    }

    private fun <R> KMutableProperty1<R, Boolean>.toCheckBox(receiver: R): JCheckBox {
        return JCheckBox(name).apply {
            isSelected = this@toCheckBox.get(receiver)
            addActionListener { this@toCheckBox.set(receiver, isSelected) }
        }
    }
    private fun <R> KProperty1<R, Boolean>.toCheckBox(receiver: R): JCheckBox {
        return JCheckBox(name).apply {
            isSelected = this@toCheckBox.get(receiver)
            isEnabled = false
        }
    }

    private fun labeled(label: String, component: JComponent) {
        add(JLabel(label))
        add(component)
    }
}