package de.hanno.hpengine.editor.grids

import com.bric.colorpicker.ColorPicker
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MaterialType
import de.hanno.hpengine.engine.model.material.SimpleMaterialInfo
import de.hanno.hpengine.engine.model.texture.FileBasedTexture2D
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.model.texture.TextureManager
import net.miginfocom.swing.MigLayout
import org.joml.Vector3f
import org.joml.Vector4f
import java.awt.Color
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.min
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

class MaterialGrid(val textureManager: TextureManager, val material: Material) : JPanel() {
    private val info = material.materialInfo as SimpleMaterialInfo

    init {
        layout = MigLayout("wrap 2")
        labeled("Name", JLabel(info.name))
        labeled("MaterialType", info::materialType.toComboBox(MaterialType.values()))
        labeled("TransparancyType", info::transparencyType.toComboBox(SimpleMaterial.TransparencyType.values()))
        labeled("Ambient", info::ambient.toSliderInput(0, 100))
        labeled("Metallic", info::metallic.toSliderInput(0, 100))
        labeled("Roughness", info::roughness.toSliderInput(0, 100))
        labeled("Transparency", info::transparency.toSliderInput(0, 100))
        labeled("ParallaxBias", info::parallaxBias.toSliderInput(0, 100))
        labeled("ParallaxScale", info::parallaxScale.toSliderInput(0, 100))

        labeled("Diffuse", info::diffuse.toColorPickerInput())
        labeled("Textures", info.toTextureComboBoxes(textureManager))

    }
}
sealed class TextureSelection(val key: String) {
    override fun toString() = key.takeLast(min(25, key.length))
    override fun equals(other: Any?): Boolean {
        return (other as? TextureSelection)?.key == key
    }

    class Selection2D(key: String, val texture: Texture): TextureSelection(key)
    class SelectionCube(key: String, val texture: Texture): TextureSelection(key)
    object None: TextureSelection("None")
}

fun SimpleMaterialInfo.toTextureComboBoxes(textureManager: TextureManager): JComponent = with(textureManager) {
    val comboBoxes = SimpleMaterial.MAP.values().map { mapType ->
        when (mapType) {
            SimpleMaterial.MAP.ENVIRONMENT -> {
                mapType to JComboBox((retrieveCubeTextureItems() + TextureSelection.None).toTypedArray()).apply {
                    addActionListener {
                        val typedSelectedItem = selectedItem as TextureSelection
                        val selected = retrieveCubeTextureItems().find { it.key == typedSelectedItem.key } ?: TextureSelection.None
                        when(selected) {
                            is TextureSelection.SelectionCube -> maps[mapType] = selected.texture
                            TextureSelection.None -> maps.remove(mapType)
                        }
                    }
                    selectedItem = retrieveInitialSelection(mapType, textureManager) { foundTexture, value ->
                        if(foundTexture == null) {
                            TextureSelection.None
                        } else {
                            TextureSelection.SelectionCube(foundTexture.key, value)
                        }
                    }
                }
            }
            else -> {
                mapType to JComboBox((retrieve2DTextureItems() + TextureSelection.None).toTypedArray()).apply {
                    addActionListener {
                        val typedSelectedItem = selectedItem as TextureSelection
                        val selected = retrieve2DTextureItems().find { it.key == typedSelectedItem.key } ?: TextureSelection.None
                        when(selected) {
                            is TextureSelection.Selection2D -> maps[mapType] = selected.texture
                            TextureSelection.None -> maps.remove(mapType)
                        }
                    }
                    selectedItem = retrieveInitialSelection(mapType, textureManager) { foundTexture, value ->
                        if(foundTexture == null) {
                            TextureSelection.None
                        } else {
                            TextureSelection.Selection2D(foundTexture.key, value)
                        }
                    }
                }
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

private fun SimpleMaterialInfo.retrieveInitialSelection(mapType: SimpleMaterial.MAP,
                                                        textureManager: TextureManager,
                                                        createSelection: (MutableMap.MutableEntry<String, Texture>?, Texture) -> TextureSelection): TextureSelection {
    return if (maps.containsKey(mapType)) {
        val value = maps[mapType]!!
        val foundTexture = textureManager.textures.entries.firstOrNull { it.value == value }
        createSelection(foundTexture, value)
    } else TextureSelection.None
}

private fun TextureManager.retrieve2DTextureItems() = retrieveTextureItems().filterIsInstance<TextureSelection.Selection2D>()
private fun TextureManager.retrieveCubeTextureItems() = retrieveTextureItems().filterIsInstance<TextureSelection.SelectionCube>()

private fun TextureManager.retrieveTextureItems(): List<TextureSelection> = textures.map {
    when (val value = it.value) {
        is Texture2D -> TextureSelection.Selection2D(it.key, value as Texture)
        is FileBasedTexture2D -> TextureSelection.Selection2D(it.key, value as Texture)
        else -> TextureSelection.SelectionCube(it.key, value)
    }
}

fun <T : Enum<*>> KMutableProperty0<T>.toComboBox(values: Array<T>): JComboBox<T> {
    return JComboBox(values).apply {
        addActionListener {
            this@toComboBox.set(this.selectedItem as T)
        }
        selectedItem = this@toComboBox.get()
    }
}

fun KProperty0<Vector3f>.toColorPickerInput(): JComponent {
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
@JvmName("toVec4ColorPickerInput")
fun KProperty0<Vector4f>.toColorPickerInput(): JComponent {
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