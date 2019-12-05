package de.hanno.hpengine.util.gui

import com.alee.extended.panel.WebTitledPanel
import com.alee.laf.button.WebToggleButton
import com.alee.laf.label.WebLabel
import com.alee.laf.slider.WebSlider
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config.SimpleConfig
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.util.gui.input.SliderInput
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import java.awt.Component
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaType

fun SimpleConfig.getButtons(engine: Engine<*>): Map<String, MutableList<JComponent>> {
    val result = mutableMapOf<String, MutableList<JComponent>>()

    result["debug"] = debug.getComponentsForProperties()
    result["effects"] = effects.getComponentsForProperties()
    result["performance"] = performance.getComponentsForProperties()
    result["quality"] = quality.getComponentsForProperties()
    result["other"] = this.getComponentsForProperties()

    val scatteringSlider = object: SliderInput("Scattering", WebSlider.HORIZONTAL, 0, 8, 1) {
        override fun onValueChange(value: Int, delta: Int) {
            engine.scene.entitySystems.get(DirectionalLightSystem::class.java).getDirectionalLight()?.scatterFactor = value.toFloat()
        }
    }

    result["debug"]?.add(getToggleButtonForProperty(GPUProfiler::PROFILING_ENABLED))
    result["debug"]?.add(getToggleButtonForProperty(GPUProfiler::PRINTING_ENABLED))
    result["debug"]?.add(getToggleButtonForProperty(GPUProfiler::DUMP_AVERAGES))

    result["effects"]?.add(scatteringSlider)

    return result
}



private fun <T: Any> T.getComponentsForProperties(): MutableList<JComponent> {
    val components = mutableListOf<JComponent>()

    for (property in this::class.memberProperties.filterIsInstance<KMutableProperty<*>>()) {
        val getter = property.getter

        when (getter.returnType.javaType) {
            Boolean::class.java -> handleBooleanInput(components, property as KMutableProperty<Boolean>, this)
            Float::class.java -> handleFloatInput(property, getter, components)
        }

    }
    return components
}

private fun <T : Any> T.handleFloatInput(property: KMutableProperty<*>, getter: KProperty.Getter<Any?>, components: MutableList<JComponent>) {
    val adjustable = property.javaField?.annotations?.filterIsInstance<Adjustable>()?.firstOrNull()
    val slider = WebSlider(WebSlider.HORIZONTAL).apply {

        if (adjustable != null) {
            minimum = adjustable.minimum
            maximum = adjustable.maximum
            minorTickSpacing = adjustable.minorTickSpacing
            majorTickSpacing = adjustable.majorTickSpacing
        } else {
            minimum = 0
            maximum = 1000
            minorTickSpacing = 250
            majorTickSpacing = 500
        }

        paintTicks = true
        paintLabels = true

        val factor = adjustable?.factor ?: 100f
        value = (getter.call(this@handleFloatInput) as Float * factor).toInt()

        addChangeListener {
            val currentValue = value * factor

            val valueAsFactor = currentValue / factor

            property.setter.call(this@handleFloatInput, valueAsFactor)
            value = valueAsFactor.toInt()
        }
    }
    val panel = getTitledPanel(slider, property.name)
    components.add(panel)
}

private fun handleBooleanInput(components: MutableList<JComponent>, property: KMutableProperty<Boolean>, subject: Any?) {
    val button = getToggleButtonForProperty(property, subject)
    components.add(button)
}

private fun getToggleButtonForProperty(property: KMutableProperty<Boolean>, subject: Any? = null): WebToggleButton {
    val currentValue = property.callGetter(subject)

    val button = WebToggleButton(property.name, currentValue).apply {
        addToggleListener(property, subject)
    }
    return button
}

fun getTitledPanel(component: Component, label: String): WebTitledPanel {
    return WebTitledPanel().apply {
        content = component
        title = WebLabel(label)
    }
}

private fun <T : Any> WebToggleButton.addToggleListener(property: KMutableProperty<Boolean>, subject: T? = null) {
    addActionListener {
        property.flip(subject)
    }
}

private fun KMutableProperty<Boolean>.flip(subject: Any?) {
    callSetter(callGetter(subject), subject)
}
private fun <T> KMutableProperty<T>.callGetter(subject: Any?): T {
    return if(subject == null) getter.call() else getter.call(subject)
}
private fun KMutableProperty<Boolean>.callSetter(value: Boolean, subject: Any?) {
    if(subject == null) {
        setter.call(!value)
    } else {
        setter.call(subject, !value)
    }
}
