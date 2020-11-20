package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.graphics.renderer.extensions.ReflectionProbe
import net.miginfocom.swing.MigLayout
import org.joml.Vector3f
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.reflect.KProperty0
import de.hanno.hpengine.engine.transform.x as xProperty
import de.hanno.hpengine.engine.transform.y as yProperty
import de.hanno.hpengine.engine.transform.z as zProperty

class ReflectionProbeGrid(val reflectionProbe: ReflectionProbe): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Entity", reflectionProbe.entity::name.toTextField())
        labeled("Extents", reflectionProbe::extents.toInput())
    }
}
fun KProperty0<Vector3f>.toInput(): JComponent = JPanel().apply {
    layout = MigLayout("wrap 2")
    labeled("X", get()::xProperty.toTextInput())
    labeled("Y", get()::yProperty.toTextInput())
    labeled("Z", get()::zProperty.toTextInput())
}