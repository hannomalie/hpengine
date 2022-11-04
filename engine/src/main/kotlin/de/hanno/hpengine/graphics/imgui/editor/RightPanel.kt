package de.hanno.hpengine.graphics.imgui.editor

import com.artemis.managers.TagManager
import de.hanno.hpengine.artemis.InvisibleComponentSystem
import de.hanno.hpengine.artemis.ModelSystem
import de.hanno.hpengine.artemis.NameComponent
import de.hanno.hpengine.artemis.TransformComponent
import de.hanno.hpengine.engine.graphics.imgui.float2Input
import de.hanno.hpengine.engine.graphics.imgui.floatInput
import de.hanno.hpengine.model.texture.OpenGLTexture2D
import imgui.ImGui
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiWindowFlags
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

fun ImGuiEditor.rightPanel(
    screenWidth: Float,
    rightPanelWidth: Float,
    screenHeight: Float
) {
    val rightPanelWidthPercentage = 0.2f
    ImGui.setNextWindowPos(screenWidth * (1.0f - rightPanelWidthPercentage), 0f)
    ImGui.setNextWindowSize(rightPanelWidth, screenHeight)
    ImGui.getStyle().windowMenuButtonPosition = ImGuiDir.None
    de.hanno.hpengine.graphics.imgui.dsl.ImGui.run {
        window("Right panel", ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.AlwaysVerticalScrollbar or ImGuiWindowFlags.AlwaysHorizontalScrollbar) {
            tabBar("Foo") {
                when (val selection = selection) {
                    null -> {
                        tab("Entity") { }
                    }
                    is MeshSelection -> {
                        tab("Entity") { }
                    }
                    is ModelComponentSelection -> {
                        tab("Entity") {
                            artemisWorld.getSystem(ModelSystem::class.java)[selection.modelComponent.modelComponentDescription]?.let {
                                if (ImGui.checkbox("Invert Y Texture Coord", it.isInvertTexCoordY)) {
                                    it.isInvertTexCoordY = !it.isInvertTexCoordY
                                }
                                val material = it.meshes.first().material
                                materialGrid(material)
                            }
                        }
                    }
                    is ModelSelection -> {
                        tab("Entity") { }
                    }
                    is NameSelection -> {
                        tab("Entity") { }
                    }
                    is SimpleEntitySelection -> tab("Entity") {
                        val system = artemisWorld.getSystem(InvisibleComponentSystem::class.java)
                        selection.components.firstIsInstanceOrNull<NameComponent>()?.run {
                            text("Name: $name")
                        }
                        checkBox("Visible", !system.invisibleComponentMapper.has(selection.entity)) { visible ->
                            system.invisibleComponentMapper.set(selection.entity, !visible)
                        }
                        selection.components.firstIsInstanceOrNull<TransformComponent>()?.run {
                            val position = transform.position
                            val positionArray = floatArrayOf(position.x, position.y, position.z)
                            ImGui.inputFloat3("Position", positionArray, "%.3f", ImGuiInputTextFlags.ReadOnly)
                        }
                    }
                    is GiVolumeSelection -> {
                        tab("Entity") { }
                    }
                    is MaterialSelection -> {
                        tab("Entity") {
                            materialGrid(selection.material)
                        }
                    }
                    Selection.None -> {
                        tab("Entity") { }
                    }
                    is OceanWaterSelection -> {
                        tab("Entity") {
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
                    }
                    is ReflectionProbeSelection -> {
                        tab("Entity") { }
                    }
                    is TransformSelection -> tab("Entity") {
                        if(ImGui.button("Reset transform")) {
                            selection.transform.transform.identity()
                        } else Unit
                    }
                    is CameraSelection -> tab("Entity") {
                        val tagManager = artemisWorld.getSystem(TagManager::class.java)
                        val isPrimaryCamera = tagManager.getEntityId(primaryCamera) == selection.entity
                        checkBox("Active", isPrimaryCamera) {
                            tagManager.register(primaryCamera, selection.entity)
                        }
                        floatInput(
                            "Near plane",
                            selection.cameraComponent.near,
                            min = 0.0001f,
                            max = 10f
                        ) { floatArray ->
                            selection.cameraComponent.near = floatArray[0]
                        }
                        floatInput("Far plane", selection.cameraComponent.far, min = 1f, max = 2000f) { floatArray ->
                            selection.cameraComponent.far = floatArray[0]
                        }
                        floatInput(
                            "Field of view",
                            selection.cameraComponent.fov,
                            min = 45f,
                            max = 170f
                        ) { floatArray ->
                            selection.cameraComponent.fov = floatArray[0]
                        }
                    }
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
                    var counter = 0
                    text("Select output")
                    if(ImGui.radioButton("Default", output, -1)) {
                        debugOutput.texture2D = null
                    }
                    gpuContext.registeredRenderTargets.forEach { target ->
                        target.renderedTextures.forEachIndexed { textureIndex, _ ->
                            if (ImGui.radioButton(target.name + "[$textureIndex]", output, counter)) {
                                (currentOutputTexture as? OpenGLTexture2D)?.let {
                                    debugOutput.texture2D = it
                                }
                            }
                            counter++
                        }
                    }
                    textureManager.texturesForDebugOutput.forEach { (name, _) ->
                        if (ImGui.radioButton(name, output, counter)) {
                            (currentOutputTexture as? OpenGLTexture2D)?.let {
                                debugOutput.texture2D = it
                            }
                        }
                        counter++
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
            }
        }
    }
}