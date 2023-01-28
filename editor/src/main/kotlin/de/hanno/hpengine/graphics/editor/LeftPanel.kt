package de.hanno.hpengine.graphics.editor

import com.artemis.Aspect
import com.artemis.ComponentManager
import com.artemis.managers.TagManager
import de.hanno.hpengine.artemis.*
import de.hanno.hpengine.artemis.model.MaterialComponent
import de.hanno.hpengine.artemis.model.ModelComponent
import de.hanno.hpengine.artemis.model.ModelSystem
import de.hanno.hpengine.artemis.forEach
import de.hanno.hpengine.component.CameraComponent
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.model.material.MaterialManager
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

fun ImGuiEditor.leftPanel(
    leftPanelYOffset: Float,
    leftPanelWidth: Float,
    screenHeight: Float
) {
    ImGui.setNextWindowPos(0f, leftPanelYOffset)
    ImGui.setNextWindowSize(leftPanelWidth, screenHeight - leftPanelYOffset)
    de.hanno.hpengine.graphics.imgui.dsl.ImGui.run {
        window("Scene", ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.AlwaysVerticalScrollbar or ImGuiWindowFlags.AlwaysHorizontalScrollbar) {
            ImGui.setNextItemOpen(true)
            treeNode("Scene") {

                val entities = artemisWorld.aspectSubscriptionManager
                    .get(Aspect.all())
                    .entities
                val componentManager = artemisWorld.getSystem(ComponentManager::class.java)!!
                entities.forEach { entityId ->
                    fillBag.clear()
                    val components = componentManager.getComponentsFor(entityId, fillBag)

                    (selection as? SimpleEntitySelection)?.let { selection ->
                        ImGui.setNextItemOpen(selection.entity == entityId)
                    }
                    Window.treeNode(
                        components.firstIsInstanceOrNull<NameComponent>()?.name
                            ?: (artemisWorld.getSystem(TagManager::class.java).getTag(entityId)
                                ?: "Entity $entityId")
                    ) {
                        Window.text("Entity") {
                            selectOrUnselect(SimpleEntitySelection(entityId, components.toList()))
                        }
                        components.forEach { component ->
                            val openNextNode = when (val selection = selection) {
                                is EntitySelection -> {
                                    when (val selection: EntitySelection = selection) {
                                        is CameraSelection -> false
                                        is MeshSelection -> false
                                        is ModelComponentSelection -> {
                                            if(component is ModelComponent) {
                                                selection.modelComponent == component
                                            } else false
                                        }
                                        is ModelSelection -> {
                                            if(component is ModelComponent) {
                                                selection.modelComponent == component
                                            } else false
                                        }
                                        is NameSelection -> false
                                        is SimpleEntitySelection -> false
                                        is TransformSelection -> false
                                    }
                                }
                                is GiVolumeSelection -> false
                                is MaterialSelection -> false
                                Selection.None -> false
                                is OceanWaterSelection -> false
                                is ReflectionProbeSelection -> false
                                null -> false
                                is TextureSelection -> false
                                is TextureManagerSelection -> false
                            }
                            val componentName = component.javaClass.simpleName
                            ImGui.setNextItemOpen(openNextNode)
                            when (component) {
                                is ModelComponent -> {
                                    text(componentName) {
                                        selectOrUnselect(ModelComponentSelection(entityId, component, components.toList()))
                                    }
                                    treeNode("Meshes") {
                                        val modelSystem = artemisWorld.getSystem(ModelSystem::class.java)!!
                                        modelSystem[component.modelComponentDescription]!!.meshes.forEach { mesh ->
                                            text(mesh.name) {
                                                selectOrUnselect(MeshSelection(entityId, mesh, component, components.toList()))
                                            }
                                        }

                                    }
                                }
                                is MaterialComponent -> text(componentName) {
                                    selectOrUnselect(MaterialSelection(component.material))
                                }
                                is NameComponent -> text(componentName) {
                                    selectOrUnselect(
                                        NameSelection(entityId, component.name, components.toList())
                                    )
                                }
                                is TransformComponent -> text(componentName) {
                                    selectOrUnselect(
                                        TransformSelection(entityId, component, components.toList())
                                    )
                                }
                                is OceanWaterComponent -> text(componentName) {
                                    selectOrUnselect(OceanWaterSelection(component))
                                }
                                is CameraComponent -> text(componentName) {
                                    selectOrUnselect(CameraSelection(entityId, component, components.toList()))
                                }
                            }
                        }
                    }
                }

                Window.treeNode("Textures") {
                    artemisWorld.systems.firstIsInstanceOrNull<TextureManagerBaseSystem>()?.let { textureManager ->
                        text("Manager") {
                            selectOrUnselect(TextureManagerSelection(textureManager))
                        }
                        treeNode("Textures") {
                            textureManager.textures.entries.sortedBy { it.key }.forEach { (key, texture) ->
                                text(key.takeLast(15)) {
                                    selectOrUnselect(TextureSelection(key, texture))
                                }
                            }
                        }
                    }
                }
                Window.treeNode("Materials") {
                    artemisWorld.getSystem(MaterialManager::class.java)?.materials?.sortedBy { it.name }
                        ?.forEach { material ->
                            text(material.name) {
                                selectOrUnselect(MaterialSelection(material))
                            }
                        }
                }
            }
        }
    }
}