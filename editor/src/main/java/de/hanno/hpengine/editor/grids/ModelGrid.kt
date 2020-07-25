package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.model.AnimatedModel
import de.hanno.hpengine.engine.model.Model
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.x
import de.hanno.hpengine.engine.transform.y
import de.hanno.hpengine.engine.transform.z
import net.miginfocom.swing.MigLayout
import org.joml.Vector3f
import javax.swing.JComboBox
import javax.swing.JFormattedTextField
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.reflect.KMutableProperty0

class ModelGrid(val model: Model<*>, val modelComponent: ModelComponent, val materialManager: MaterialManager): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Path", JLabel(model.path.takeLast(15)))
        labeled("Material", model::material.toComboBox())
        labeled("Unique Vertices", JLabel(model.uniqueVertices.size.toString()))
        modelComponent.spatial.minMax.toInputs().forEach { (label, component) ->
            labeled(label, component)
        }

        if(model is AnimatedModel) {
            labeled("Animations", JLabel(model.animations.size.toString()))
            model.animations.forEach {
                labeled(it.key, JLabel(""))
                labeled("", it.value::fps.toSliderInput(0,10000))
            }
        }
    }

    fun KMutableProperty0<Material>.toComboBox(): JComboBox<SimpleMaterial> {
        return JComboBox(materialManager.MATERIALS.values.toTypedArray()).apply {
            addActionListener {
                this@toComboBox.set(this.selectedItem as SimpleMaterial)
            }
            selectedItem = this@toComboBox.get()
        }
    }
}

fun AABB.toInputs(): List<Pair<String, JFormattedTextField>> {
    val xMinInput = JFormattedTextField(this@toInputs.localMin.x).apply {
        columns = 5
        addPropertyChangeListener("value") { this@toInputs.localMin = Vector3f(it.newValue as Float, this@toInputs.localMin.y, this@toInputs.localMin.z) }
    }
    val yMinInput = JFormattedTextField(this@toInputs.localMin.y).apply {
        columns = 5
        addPropertyChangeListener("value") { this@toInputs.localMin = Vector3f(this@toInputs.localMin.x, it.newValue as Float, this@toInputs.localMin.z) }
    }
    val zMinInput = JFormattedTextField(this@toInputs.localMin.z).apply {
        columns = 5
        addPropertyChangeListener("value") { this@toInputs.localMin = Vector3f(this@toInputs.localMin.x, this@toInputs.localMin.y, it.newValue as Float) }
    }

    val xMaxInput = JFormattedTextField(this@toInputs.localMax.x).apply {
        columns = 5
        addPropertyChangeListener("value") { this@toInputs.localMax = Vector3f(it.newValue as Float, this@toInputs.localMax.y, this@toInputs.localMax.z) }
    }
    val yMaxInput = JFormattedTextField(this@toInputs.localMax.y).apply {
        columns = 5
        addPropertyChangeListener("value") { this@toInputs.localMax = Vector3f(this@toInputs.localMax.x, it.newValue as Float, this@toInputs.localMax.z) }
    }
    val zMaxInput = JFormattedTextField(this@toInputs.localMax.z).apply {
        columns = 5
        addPropertyChangeListener("value") { this@toInputs.localMax = Vector3f(this@toInputs.localMax.x, this@toInputs.localMax.y, it.newValue as Float) }
    }
    return listOf(
        Pair("MinX", xMinInput),
        Pair("MinY", yMinInput),
        Pair("MinZ", zMinInput),
        Pair("MaxX", xMaxInput),
        Pair("MaxY", yMaxInput),
        Pair("MaxZ", zMaxInput)
    )
}
