package de.hanno.hpengine.graphics.editor.select

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.engine.graphics.imgui.floatInput
import de.hanno.hpengine.graphics.editor.ImGuiEditor
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.editor.textureSelection
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.model.AnimatedModel
import de.hanno.hpengine.model.MaterialComponent
import de.hanno.hpengine.model.Model
import de.hanno.hpengine.model.StaticModel
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.model.material.WorldSpaceTexCoords
import imgui.ImGui.*
import org.joml.Vector3f
import org.koin.core.annotation.Single
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

data class MaterialSelection(val material: Material): Selection {
    override fun toString() = material.name
}
data class MaterialManagerSelection(val materialSystem: MaterialSystem): Selection

@Single(binds = [EditorExtension::class])
class MaterialEditorExtension(
    private val textureManager: TextureManagerBaseSystem,
    private val materialSystem: MaterialSystem,
): EditorExtension {
    override fun getSelection(any: Any, components: Bag<Component>?) = when (any) {
        is MaterialSystem -> {
            MaterialManagerSelection(materialSystem)
        }
        else -> null
    }

    override fun ImGuiEditor.renderLeftPanelTopLevelNode() {
        Window.treeNode("Materials") {
            materialSystem.materials.sortedBy { it.name }.forEach { material ->
                text(material.name) {
                    selection = MaterialSelection(material)
                }
            }
        }
    }
    override fun getSelectionForComponentOrNull(component: Component, entity: Int, components: Bag<Component>): Selection? {
        return if(component is MaterialComponent) {
            MaterialSelection(component.material)
        } else null
    }

    override fun Window.renderRightPanel(selection: Selection?) = when (selection) {
        is MaterialSelection -> {
            materialGrid(selection.material, textureManager)
            true
        }
        else -> false
    }
}

fun Window.modelGrid(model: Model<*>) {
    when(model) {
        is AnimatedModel -> {
            text("Animations")
            model.animationController.animations.forEach { name, animation ->
                spacing()
                text(name)
                floatInput("fps", animation.fps, 0f, 100f) {
                    animation.fps = it.first()
                }
            }
        }
        is StaticModel -> {}
    }
}
fun Window.materialGrid(material: Material, textureManager: TextureManagerBaseSystem) {
    text(material.name)
    colorPicker3("Albedo", material::diffuse)
    floatInput("Roughness", material.roughness) { floatArray -> material.roughness = floatArray[0] }
    floatInput("Metallic", material.metallic) { floatArray -> material.metallic = floatArray[0] }
    floatInput("Ambient", material.ambient) { floatArray -> material.ambient = floatArray[0] }
    floatInput("Transparency", material::transparency)
    floatInput("ParallaxScale", material::parallaxScale)
    floatInput("ParallaxBias", material::parallaxBias)
    floatInput("UVScaleX", material.uvScale::x, 0.01f, 100f)
    floatInput("UVScaleY", material.uvScale::y, 0.01f, 100f)
    floatInput("LODFactor", material::lodFactor, 1f, 100f)
    comboBox("WorldSpaceTexCoords", material::worldSpaceTexCoords, WorldSpaceTexCoords.entries)
    comboBox("Type", material::materialType, Material.MaterialType.entries)
    comboBox("TransparencyType", material::transparencyType, Material.TransparencyType.entries)
    checkbox("BackFaceCulling", material::cullBackFaces)
    checkbox("FrontFaceCulling", material::cullFrontFaces)
    checkbox("DepthTest", material::depthTest)
    checkbox("Write depth", material::writesDepth)
    comboBox("EnvironmentMapType", material::environmentMapType, Material.ENVIRONMENTMAP_TYPE.entries)
    checkbox("CastShadows", material::isShadowCasting)
    textureSelection(material, textureManager)
}

private fun colorPicker3(label: String, property: KProperty0<Vector3f>) {
    val value = property.get()
    val colors = floatArrayOf(value.x, value.y, value.z)
    if (colorPicker3(label, colors)) {
        value.x = colors[0]
        value.y = colors[1]
        value.z = colors[2]
    }
}
private fun checkbox(label: String, property: KMutableProperty0<Boolean>) {
    if (checkbox(label, property.get())) {
        property.set(!property.get())
    }
}
private fun floatInput(label: String, property: KMutableProperty0<Float>, min: Float, max: Float) {
    floatInput(label, property.get(), min, max) { floatArray ->
        property.set(floatArray[0])
    }
}

private fun <T: Enum<*>> comboBox(label: String, property: KMutableProperty0<T>, entries: List<T>) {
    if (beginCombo(label, property.get().toString())) {
        entries.forEach { type ->
            val selected = property.get() == type
            if (selectable(type.toString(), selected)) {
                property.set(type)
            }
            if (selected) {
                setItemDefaultFocus()
            }
        }
        endCombo()
    }
}


