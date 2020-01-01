package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.config.Button
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.event.GlobalDefineChangedEvent
import de.hanno.hpengine.engine.event.bus.EventBus
import net.miginfocom.swing.MigLayout
import java.awt.Component
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.javaType
import javax.swing.BorderFactory
import javax.swing.JButton
import kotlin.reflect.full.findAnnotation

class ConfigGrid(val config: ConfigImpl, val eventBus: EventBus): JPanel() {
    init {
        layout = MigLayout("wrap 1")
        getInputsPanelForObject(config.debug, "Debug")?.let { add(it) }
        getInputsPanelForObject(config.effects, "Effects")?.let { add(it) }
        getInputsPanelForObject(config.performance, "Performance")?.let { add(it) }
        getInputsPanelForObject(config.quality, "Quality")?.let { add(it) }
        getInputsPanelForObject(config.profiling, "Profiling")?.let { add(it) }
    }

    private inline fun <reified T> getInputsPanelForObject(debugConfig: T, legend: String): JPanel? {

        val checkBoxes = T::class.declaredMembers.mapNotNull {
            if (it.returnType.javaType == Boolean::class.java) {
                when (it) {
                    is KMutableProperty1<*, *> -> {
                        val element: JComponent? = if(it.findAnnotation<Button>() != null) {
                            (it as? KMutableProperty1<T, Boolean>)?.toButton(debugConfig)
                        } else {
                            (it as? KMutableProperty1<T, Boolean>)?.toCheckBox(debugConfig)
                        }
                        element
                    }
                    else -> (it as? KProperty1<T, Boolean>)?.toCheckBox(debugConfig)
                }
            } else null
        }
        return if(checkBoxes.isNotEmpty()) {
            JPanel().apply {
                layout = MigLayout("wrap 4")
                border = BorderFactory.createTitledBorder(legend)
                checkBoxes.forEach { this.add(it) }
            }
        } else null
    }

    private fun <R> KMutableProperty1<R, Boolean>.toCheckBox(receiver: R): JCheckBox {
        return JCheckBox(name).apply {
            isSelected = this@toCheckBox.get(receiver)
            addActionListener {
                this@toCheckBox.set(receiver, isSelected)
                eventBus.post(GlobalDefineChangedEvent())
            }
        }
    }
    private fun <R> KMutableProperty1<R, Boolean>.toButton(receiver: R): JButton {
        return JButton(name).apply {
            addActionListener {
                this@toButton.set(receiver, true)
            }
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