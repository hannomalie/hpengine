package de.hanno.hpengine.graphics.imgui.editor

import com.artemis.Aspect
import com.artemis.ComponentManager
import com.artemis.managers.TagManager
import de.hanno.hpengine.artemis.*
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.model.material.MaterialManager
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
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
            treeNode("Scene") {

                val entities = artemisWorld.aspectSubscriptionManager
                    .get(Aspect.all())
                    .entities
                val componentManager = artemisWorld.getSystem(ComponentManager::class.java)!!
                entities.forEach { entityId ->
                    fillBag.clear()
                    val components = componentManager.getComponentsFor(entityId, fillBag)

                    Window.treeNode(
                        components.firstIsInstanceOrNull<NameComponent>()?.name
                            ?: (artemisWorld.getSystem(TagManager::class.java).getTag(entityId)
                                ?: "Entity $entityId")
                    ) {
                        Window.text("Entity") {
                            selectOrUnselect(SimpleEntitySelection(entityId, components.toList()))
                        }
                        components.forEach { component ->
                            text(component.javaClass.simpleName) {
                                when (component) {
                                    is ModelComponent -> {
                                        selectOrUnselect(ModelComponentSelection(entityId, component))
                                    }
                                    is MaterialComponent -> {
                                        selectOrUnselect(MaterialSelection(component.material))
                                    }
                                    is NameComponent -> selectOrUnselect(
                                        NameSelection(entityId, component.name)
                                    )
                                    is TransformComponent -> selectOrUnselect(
                                        TransformSelection(entityId, component)
                                    )
                                    is OceanWaterComponent -> selectOrUnselect(OceanWaterSelection(component))
                                    is CameraComponent -> selectOrUnselect(CameraSelection(entityId, component))
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