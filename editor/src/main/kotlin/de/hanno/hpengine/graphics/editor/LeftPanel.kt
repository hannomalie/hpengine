package de.hanno.hpengine.graphics.editor

import com.artemis.Aspect
import com.artemis.Component
import com.artemis.ComponentManager
import com.artemis.managers.TagManager
import com.artemis.utils.Bag
import de.hanno.hpengine.artemis.forEach
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.graphics.imgui.dsl.Window
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import de.hanno.hpengine.graphics.editor.select.*

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
                    val fillBag = Bag<Component>()
                    val components = componentManager.getComponentsFor(entityId, fillBag)

                    // TODO: Cover case where already opened nodes should remain open
                    when(val selection = selection) {
                        is EntitySelection -> ImGui.setNextItemOpen(selection.entity == entityId)
                    }

                    val tagManager = artemisWorld.getSystem(TagManager::class.java)
                    val nameComponentName = components.firstIsInstanceOrNull<NameComponent>()?.name

                    val nodeLabel = nameComponentName?: (tagManager.getTag(entityId) ?: "Entity $entityId")
                    Window.treeNode(nodeLabel) {
                        Window.text("Entity") {
                            selectOrUnselect(SimpleEntitySelection(entityId, components))
                        }
                        components.forEach { component ->
                            var anySelectorHasRenderedANode = false
                            extensions.forEach { selector ->
                                selector.run {
                                    val renderedSth = renderLeftPanelComponentNode(component, entityId, components, selection)
                                    if(renderedSth) {
                                        anySelectorHasRenderedANode = true
                                    }
                                }
                            }
                            if(!anySelectorHasRenderedANode) {
                                val componentName = component.javaClass.simpleName
                                text(componentName) {
                                    selectOrUnselect(DefaultSelection(component))
                                }
                            }
                        }
                    }
                }

                extensions.forEach {
                    it.run {
                        renderLeftPanelTopLevelNode()
                    }
                }
            }
        }
    }
}