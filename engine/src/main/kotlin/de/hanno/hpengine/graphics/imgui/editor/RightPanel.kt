package de.hanno.hpengine.graphics.imgui.editor

import com.artemis.Component
import com.artemis.World
import com.artemis.managers.TagManager
import de.hanno.hpengine.artemis.InvisibleComponentSystem
import de.hanno.hpengine.artemis.ModelSystem
import de.hanno.hpengine.artemis.NameComponent
import de.hanno.hpengine.artemis.TransformComponent
import de.hanno.hpengine.engine.graphics.imgui.float2Input
import de.hanno.hpengine.engine.graphics.imgui.floatInput
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.model.material.MaterialManager
import imgui.ImGui
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiWindowFlags
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

context(GpuContext)
fun ImGuiEditor.rightPanel(
    screenWidth: Float,
    rightPanelWidth: Float,
    screenHeight: Float,
    editorConfig: EditorConfig
) {
    val rightPanelWidthPercentage = 0.2f
    ImGui.setNextWindowPos(screenWidth * (1.0f - rightPanelWidthPercentage), 0f)
    ImGui.setNextWindowSize(rightPanelWidth, screenHeight)
    ImGui.getStyle().windowMenuButtonPosition = ImGuiDir.None
    de.hanno.hpengine.graphics.imgui.dsl.ImGui.run {
        window("Right panel", ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.AlwaysVerticalScrollbar or ImGuiWindowFlags.AlwaysHorizontalScrollbar) {
            tabBar("Foo") {
                when(val selection = selection) {
                    null -> tab("Entity") { }
                    is EntitySelection -> {
                        val entity = selection.entity
                        val invisibleComponentSystem = artemisWorld.getSystem(InvisibleComponentSystem::class.java)
                        val components = selection.components

                        when(val entitySelection: EntitySelection = selection) {
                            is CameraSelection -> tab("Entity") {
                                cameraInputs(entitySelection, artemisWorld)
                            }
                            is MeshSelection -> tab("Entity") {
                                entityInputs(components, invisibleComponentSystem, entity)
                                text(entitySelection.mesh.name)

                                if (ImGui.beginCombo("Material", entitySelection.mesh.material.name)) {
                                    artemisWorld.getSystem(MaterialManager::class.java).materials.distinctBy { it.name }.forEach { material ->
                                        val selected = entitySelection.mesh.material.name == material.name
                                        if (ImGui.selectable(material.name, selected)) {
                                            entitySelection.mesh.material = material
                                        }
                                        if (selected) {
                                            ImGui.setItemDefaultFocus()
                                        }
                                    }
                                    ImGui.endCombo()
                                }
                            }
                            is ModelComponentSelection -> tab("Entity") {
                                artemisWorld.getSystem(ModelSystem::class.java)[entitySelection.modelComponent.modelComponentDescription]?.let {
                                    if (ImGui.checkbox("Invert Y Texture Coord", it.isInvertTexCoordY)) {
                                        it.isInvertTexCoordY = !it.isInvertTexCoordY
                                    }
                                    val material = it.meshes.first().material
                                    materialGrid(material)
                                }
                            }
                            is ModelSelection -> tab("Entity") { }
                            is NameSelection -> tab("Entity") { }
                            is SimpleEntitySelection -> tab("Entity") {
                                entityInputs(components, invisibleComponentSystem, entity)
                            }
                            is TransformSelection -> tab("Entity") {
                                if(ImGui.button("Reset transform")) {
                                    entitySelection.transform.transform.identity()
                                } else Unit
                            }
                        }
                    }
                    is GiVolumeSelection -> tab("Entity") { }
                    is MaterialSelection -> tab("Entity") {
                        materialGrid(selection.material)
                    }
                    is OceanWaterSelection -> tab("Entity") {
                        oceanWaterInputs(selection)
                    }
                    is ReflectionProbeSelection -> tab("Entity") { }
                    Selection.None -> tab("Entity") { }
                }.let {}

                tab("Output") {
                    if (ImGui.beginCombo("Mipmap Level", debugOutput.mipmapLevel.toString())) {
                        repeat(10) {
                            val selected = debugOutput.mipmapLevel == it
                            if (ImGui.selectable(it.toString(), selected)) {
                                debugOutput.mipmapLevel = it
                            }
                            if (selected) {
                                ImGui.setItemDefaultFocus()
                            }
                        }
                        ImGui.endCombo()
                    }
                    text("Select output")
                    if(ImGui.radioButton("Default", output, -1)) {
                        debugOutput.texture2D = null
                    }
                    textureOutputOptions.forEachIndexed { index, option ->
                        if (ImGui.radioButton(option.identifier, output, index)) {
                            debugOutput.texture2D = option.texture
                        }
                    }
                }
                tab("RenderExtensions") {
                    deferredRenderExtensionConfig.run {
                        renderExtensions.forEach {
                            if (ImGui.checkbox(it.javaClass.simpleName, it.enabled)) {
                                it.enabled = !it.enabled
                            }
                        }
                    }
                }
                configTab(config, window)
                renderTab()
                tab("Editor") {
                    if (ImGui.beginCombo("Selection Mode", editorConfig.selectionMode.toString())) {
                        SelectionMode.values().forEach {
                            val selected = editorConfig.selectionMode == it
                            if (ImGui.selectable(it.toString(), selected)) {
                                editorConfig.selectionMode = it
                            }
                            if (selected) {
                                ImGui.setItemDefaultFocus()
                            }
                        }
                        ImGui.endCombo()
                    }
                }
            }
        }
    }
}

private fun Window.cameraInputs(
    entitySelection: CameraSelection,
    world: World,
) {
    val tagManager = world.getSystem(TagManager::class.java)
    val isPrimaryCamera = tagManager.getEntityId(primaryCamera) == entitySelection.entity
    checkBox("Active", isPrimaryCamera) {
        tagManager.register(primaryCamera, entitySelection.entity)
    }
    floatInput(
        "Near plane",
        entitySelection.cameraComponent.near,
        min = 0.0001f,
        max = 10f
    ) { floatArray ->
        entitySelection.cameraComponent.near = floatArray[0]
    }
    floatInput("Far plane", entitySelection.cameraComponent.far, min = 1f, max = 2000f) { floatArray ->
        entitySelection.cameraComponent.far = floatArray[0]
    }
    floatInput(
        "Field of view",
        entitySelection.cameraComponent.fov,
        min = 45f,
        max = 170f
    ) { floatArray ->
        entitySelection.cameraComponent.fov = floatArray[0]
    }
}

private fun Window.oceanWaterInputs(selection: OceanWaterSelection) {
    val oceanWater = selection.oceanWater
    val colors = floatArrayOf(
        oceanWater.albedo.x,
        oceanWater.albedo.y,
        oceanWater.albedo.z
    )
    if (ImGui.colorPicker3("Albedo", colors)) {
        oceanWater.albedo.x = colors[0]
        oceanWater.albedo.y = colors[1]
        oceanWater.albedo.z = colors[2]
    }
    floatInput("Amplitude", oceanWater.amplitude, min = 0.1f, max = 5f) { floatArray ->
        oceanWater.amplitude = floatArray[0]
    }
    floatInput("Windspeed", oceanWater.windspeed, min = 0.0f, max = 250f) { floatArray ->
        oceanWater.windspeed = floatArray[0]
    }
    float2Input(
        "Direction",
        oceanWater.direction.x,
        oceanWater.direction.y,
        min = 0.0f,
        max = 1.0f
    ) { floatArray ->
        oceanWater.direction.x = floatArray[0]
        oceanWater.direction.y = floatArray[1]
    }
    floatInput(
        "Wave Height",
        oceanWater.waveHeight,
        min = 0.0f,
        max = 10.0f
    ) { floatArray ->
        oceanWater.waveHeight = floatArray[0]
    }
    checkBox("Choppy", oceanWater.choppy) { boolean ->
        oceanWater.choppy = boolean
    }
    floatInput("Choppiness", oceanWater.choppiness, min = 0.0f, max = 1.0f) { floatArray ->
        oceanWater.choppiness = floatArray[0]
    }
    floatInput("Time Factor", oceanWater.timeFactor, min = 0.0f, max = 10f) { floatArray ->
        oceanWater.timeFactor = floatArray[0]
    }
}

private fun Window.entityInputs(
    components: List<Component>,
    system: InvisibleComponentSystem,
    entity: Int
) {
    components.firstIsInstanceOrNull<NameComponent>()?.run {
        text("Name: $name")
    }
    checkBox("Visible", !system.invisibleComponentMapper.has(entity)) { visible ->
        system.invisibleComponentMapper.set(entity, !visible)
    }
    components.firstIsInstanceOrNull<TransformComponent>()?.run {
        val position = transform.position
        val positionArray = floatArrayOf(position.x, position.y, position.z)
        ImGui.inputFloat3("Position", positionArray, "%.3f", ImGuiInputTextFlags.ReadOnly)
    }
}