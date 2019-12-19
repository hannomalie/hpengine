package de.hanno.hpengine.editor

import com.bric.colorpicker.ColorPicker
import de.hanno.hpengine.editor.TextureSelection.Companion.noneSelection
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MaterialType
import de.hanno.hpengine.engine.model.material.SimpleMaterialInfo
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.TextureDimension2D
import de.hanno.hpengine.engine.model.texture.TextureManager
import net.miginfocom.swing.MigLayout
import org.joml.Vector3f
import java.awt.Color
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.min
import kotlin.reflect.KMutableProperty0

class MaterialGrid(val textureManager: TextureManager, val material: Material) : JPanel() {
    private val info = material.materialInfo as SimpleMaterialInfo

    init {
        layout = MigLayout("wrap 2")
        labeled("Name", JLabel(info.name))
        labeled("MaterialType", info::materialType.toComboBox(MaterialType.values()))
        labeled("Ambient", info::ambient.toSliderInput(0, 100))
        labeled("Metallic", info::metallic.toSliderInput(0, 100))
        labeled("Roughness", info::roughness.toSliderInput(0, 100))
        labeled("ParallaxBias", info::parallaxBias.toSliderInput(0, 100))
        labeled("ParallaxScale", info::parallaxScale.toSliderInput(0, 100))

        labeled("Diffuse", info::diffuse.toColorPickerInput())
        labeled("Textures", info.toTextureComboBoxes(textureManager))

    }
}
class TextureSelection(val key: String, val texture: Texture<*>?) {
    override fun toString() = key.takeLast(min(25, key.length))
    override fun equals(other: Any?): Boolean {
        return (other as? TextureSelection)?.key == key
    }
    companion object {
        val noneSelection = TextureSelection("None", null)
    }
}

fun SimpleMaterialInfo.toTextureComboBoxes(textureManager: TextureManager): JComponent = with(textureManager) {
    val initialTextures = retrieve2DTextureItems().toTypedArray()
    val comboBoxes = SimpleMaterial.MAP.values().mapNotNull { mapType ->
        when (mapType) {
            SimpleMaterial.MAP.ENVIRONMENT -> null
            else -> {
                val comboBox = JComboBox(initialTextures).apply {
                    addActionListener {
                        val typedSelectedItem = selectedItem as TextureSelection
                        val selected = retrieve2DTextureItems().find { it.key == typedSelectedItem.key }!!
                        if (selected == noneSelection) {
                            maps.remove(mapType)
                        } else {
                            maps[mapType] = selected.texture as Texture<TextureDimension2D>
                        }
                    }
                    val newSelection: TextureSelection = if (maps.containsKey(mapType)) {
                        val value = maps[mapType]!!
                        val foundTexture = textureManager.textures.entries.first { it.value == value }
                        TextureSelection(foundTexture.key, value)
                    } else noneSelection
                    selectedItem = newSelection
                }
                mapType to comboBox
            }
        }
    }
    return JPanel().apply {
        layout = MigLayout("wrap 2")
        comboBoxes.forEach { (map, combobox) ->
            labeled(map.toString().toLowerCase(), combobox)
        }
    }
}

private fun TextureManager.retrieve2DTextureItems() =
        textures.filterValues { it.dimension is TextureDimension2D }.map { TextureSelection(it.key, it.value) } + noneSelection

fun <T : Enum<*>> KMutableProperty0<T>.toComboBox(values: Array<T>): JComboBox<T> {
    return JComboBox(values).apply {
        addActionListener {
            this@toComboBox.set(this.selectedItem as T)
        }
        selectedItem = this@toComboBox.get()
    }
}

fun KMutableProperty0<Vector3f>.toColorPickerInput(): JComponent {
    return ColorPicker(false, false).apply {
        val initialValue = get()
        color = Color(
                (initialValue.x * 255f).toInt(),
                (initialValue.y * 255f).toInt(),
                (initialValue.z * 255f).toInt()
        )
        addColorListener {
            get().x = it.color.red.toFloat() / 255f
            get().y = it.color.green.toFloat() / 255f
            get().z = it.color.blue.toFloat() / 255f
        }
    }

}