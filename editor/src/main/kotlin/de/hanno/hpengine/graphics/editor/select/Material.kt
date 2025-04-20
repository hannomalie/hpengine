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
import org.koin.core.annotation.Single

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
    val colors = floatArrayOf(
        material.diffuse.x,
        material.diffuse.y,
        material.diffuse.z
    )
    if (colorPicker3("Albedo", colors)) {
        material.diffuse.x = colors[0]
        material.diffuse.y = colors[1]
        material.diffuse.z = colors[2]
    }
    floatInput("Roughness", material.roughness) { floatArray -> material.roughness = floatArray[0] }
    floatInput("Metallic", material.metallic) { floatArray -> material.metallic = floatArray[0] }
    floatInput("Ambient", material.ambient) { floatArray -> material.ambient = floatArray[0] }
    floatInput("Transparency", material.transparency) { floatArray ->
        material.transparency = floatArray[0]
    }
    floatInput("ParallaxScale", material.parallaxScale) { floatArray ->
        material.parallaxScale = floatArray[0]
    }
    floatInput("ParallaxBias", material.parallaxBias) { floatArray ->
        material.parallaxBias = floatArray[0]
    }
    floatInput("UVScaleX", material.uvScale.x, 0.01f, 100f) { floatArray ->
        material.uvScale.x = floatArray[0]
    }
    floatInput("UVScaleY", material.uvScale.y, 0.01f, 100f) { floatArray ->
        material.uvScale.y = floatArray[0]
    }
    floatInput("LODFactor", material.lodFactor, 1f, 100f) { floatArray -> material.lodFactor = floatArray[0] }
    if (beginCombo("WorldSpaceTexCoords", material.worldSpaceTexCoords.toString())) {
        WorldSpaceTexCoords.entries.forEach { type ->
            val selected = material.worldSpaceTexCoords == type
            if (selectable(type.toString(), selected)) {
                material.worldSpaceTexCoords = type
            }
            if (selected) {
                setItemDefaultFocus()
            }
        }
        endCombo()
    }
    if (beginCombo("Type", material.materialType.toString())) {
        Material.MaterialType.entries.forEach { type ->
            val selected = material.materialType == type
            if (selectable(type.toString(), selected)) {
                material.materialType = type
            }
            if (selected) {
                setItemDefaultFocus()
            }
        }
        endCombo()
    }
    if (beginCombo("TransparencyType", material.transparencyType.toString())) {
        Material.TransparencyType.entries.forEach { type ->
            val selected = material.transparencyType == type
            if (selectable(type.toString(), selected)) {
                material.transparencyType = type
            }
            if (selected) {
                setItemDefaultFocus()
            }
        }
        endCombo()
    }
    if (checkbox("BackFaceCulling", material.cullBackFaces)) {
        material.cullBackFaces = !material.cullBackFaces
    }
    if (checkbox("FrontFaceCulling", material.cullFrontFaces)) {
        material.cullFrontFaces = !material.cullFrontFaces
    }
    if (checkbox("DepthTest", material.depthTest)) {
        material.depthTest = !material.depthTest
    }
    if (beginCombo("EnvironmentMapType", material.environmentMapType.toString())) {
        Material.ENVIRONMENTMAP_TYPE.entries.forEach { type ->
            val selected = material.environmentMapType == type
            if (selectable(type.toString(), selected)) {
                material.environmentMapType = type
            }
            if (selected) {
                setItemDefaultFocus()
            }
        }
        endCombo()
    }
    if (checkbox("CastShadows", material.isShadowCasting)) {
        material.isShadowCasting = !material.isShadowCasting
    }
    textureSelection(material, textureManager)
}
