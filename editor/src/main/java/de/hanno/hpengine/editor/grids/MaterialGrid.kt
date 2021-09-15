package de.hanno.hpengine.editor.grids

import com.bric.colorpicker.ColorPicker
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.material.MaterialInfo
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MaterialType
import de.hanno.hpengine.engine.model.texture.FileBasedTexture2D
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.model.texture.TextureManager
import net.miginfocom.swing.MigLayout
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.min
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

var Vector2f.myX
    get() = x
    set(value) { x = value }
var Vector2f.myY
    get() = y
    set(value) { y = value }

class MaterialGrid(val programManager: ProgramManager<OpenGl>,
                   val textureManager: TextureManager,
                   val material: Material) : JPanel() {
    private val info = material.materialInfo

    private val heightMappingFirstpassProgram = programManager.heightMappingFirstPassProgram

    var useHeightMapping: Boolean
        get() = info.program == heightMappingFirstpassProgram
        set(value) {
            info.program = if(value) heightMappingFirstpassProgram else null
        }

    init {
        layout = MigLayout("wrap 2")
        labeled("Name", JLabel(material.name))
        labeled("MaterialType", info::materialType.toComboBox(MaterialType.values()))
        labeled("TransparancyType", info::transparencyType.toComboBox(SimpleMaterial.TransparencyType.values()))
        topLabeled("Ambient", info::ambient.toSliderInput(0f, 1f))
        topLabeled("Metallic", info::metallic.toSliderInput(0f, 1f))
        topLabeled("Roughness", info::roughness.toSliderInput(0f, 1f))
        topLabeled("Transparency", info::transparency.toSliderInput(0f, 1f))
        topLabeled("ParallaxBias", info::parallaxBias.toSliderInput(0f, 1f))
        topLabeled("ParallaxScale", info::parallaxScale.toSliderInput(0f, 1f))
        topLabeled("UV Scale X", info.uvScale::myX.toSliderInput(0f, 5f))
        topLabeled("UV Scale y", info.uvScale::myY.toSliderInput(0f, 5f))
        topLabeled("LOD Factor", info::lodFactor.toSliderInput(0f, 500f))
        add(info::useWorldSpaceXZAsTexCoords.toCheckBox(), "span 2")
        add(::useHeightMapping.toCheckBox(), "span 2")

        topLabeled("Diffuse", info::diffuse.toColorPickerInput())
        topLabeled("Textuers", info.toTextureComboBoxes(textureManager))

    }
}


fun JComponent.topLabeled(label: String, component: JComponent) {
    add(JLabel(label), "wrap")
    add(component, "span 2")
}
sealed class TextureSelection(val key: String) {
    override fun toString() = key.takeLast(min(25, key.length))
    override fun equals(other: Any?): Boolean = (other as? TextureSelection)?.key == key
    override fun hashCode(): Int = key.hashCode()

    class Selection2D(key: String, val texture: Texture): TextureSelection(key)
    class SelectionCube(key: String, val texture: Texture): TextureSelection(key)
    class Other(val texture: Texture? = null): TextureSelection("Other")
    object None: TextureSelection("None")
}

fun MaterialInfo.toTextureComboBoxes(textureManager: TextureManager): JComponent = with(textureManager) {
    val comboBoxes = SimpleMaterial.MAP.values().map { mapType ->
        when (mapType) {
            SimpleMaterial.MAP.ENVIRONMENT -> {
                mapType to JComboBox((retrieveCubeTextureItems() + TextureSelection.None).toTypedArray()).apply {
                    addActionListener {
                        val typedSelectedItem = selectedItem as TextureSelection
                        val selected = retrieveCubeTextureItems().find { it.key == typedSelectedItem.key }
                                ?: TextureSelection.None
                        when (selected) {
                            is TextureSelection.SelectionCube -> maps[mapType] = selected.texture
                            TextureSelection.None -> maps.remove(mapType)
                        }
                    }
                    selectedItem = retrieveInitialSelection(mapType, textureManager) { foundTexture, value ->
                        when (foundTexture) {
                            null -> TextureSelection.Other(value)
                            else -> TextureSelection.SelectionCube(foundTexture.key, value)
                        }
                    }
                }
            }
            else -> {
                mapType to JComboBox((retrieve2DTextureItems() + TextureSelection.Other() + TextureSelection.None).toTypedArray()).apply {
                    addActionListener {
                        val typedSelectedItem = selectedItem as TextureSelection
                        val foundTextureOrNull = retrieve2DTextureItems().find { it.key == typedSelectedItem.key }
                        when(val selected = foundTextureOrNull ?: (if(typedSelectedItem is TextureSelection.Other) typedSelectedItem else TextureSelection.None)) {
                            is TextureSelection.Selection2D -> maps[mapType] = selected.texture
                            TextureSelection.None -> maps.remove(mapType)
                        }
                    }
                    selectedItem = retrieveInitialSelection(mapType, textureManager) { foundTexture, value ->
                        when (foundTexture) {
                            null -> TextureSelection.Other(value)
                            else -> TextureSelection.Selection2D(foundTexture.key, value)
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

private fun MaterialInfo.retrieveInitialSelection(mapType: SimpleMaterial.MAP,
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

fun KProperty0<Vector3f>.toColorPickerInput(): JComponent = ColorPicker(false, false).apply {
    preferredSize = Dimension(200, 160)
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
@JvmName("toVec4ColorPickerInput")
fun KProperty0<Vector4f>.toColorPickerInput(): JComponent = ColorPicker(false, false).apply {
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