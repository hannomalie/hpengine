package de.hanno.hpengine.editor.grids

import de.hanno.hpengine.engine.model.AnimatedModel
import de.hanno.hpengine.engine.model.Model
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import net.miginfocom.swing.MigLayout
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.reflect.KMutableProperty0

class ModelGrid(val model: Model<*>, val materialManager: MaterialManager): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Path", JLabel(model.path.takeLast(15)))
        labeled("Material", model::material.toComboBox())
        labeled("Unique Vertices", JLabel(model.uniqueVertices.size.toString()))

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
