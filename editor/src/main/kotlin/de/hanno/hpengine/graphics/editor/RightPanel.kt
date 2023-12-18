package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.graphics.RenderManager
import de.hanno.hpengine.graphics.RenderSystemsConfig
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import imgui.ImGui
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiWindowFlags
import imgui.flag.ImGuiWindowFlags.*

private const val rightPanelFlags =
    NoCollapse or
    NoResize or
    NoTitleBar or
    HorizontalScrollbar

fun ImGuiEditor.rightPanel(
    screenWidth: Float,
    rightPanelWidth: Float,
    screenHeight: Float,
    editorConfig: EditorConfig,
    renderSystemsConfig: RenderSystemsConfig,
    renderManager: RenderManager,
    editorExtensions: List<EditorExtension>
) {
    val rightPanelWidthPercentage = 0.2f
    ImGui.setNextWindowPos(screenWidth * (1.0f - rightPanelWidthPercentage), 0f)
    ImGui.setNextWindowSize(rightPanelWidth, screenHeight)
    ImGui.getStyle().windowMenuButtonPosition = ImGuiDir.None
    ImGui.setNextWindowBgAlpha(1.0f)
    de.hanno.hpengine.graphics.imgui.dsl.ImGui.run {
        window("Right panel", rightPanelFlags) {
            tabBar("Foo") {

                tab("Entity") {
                    editorExtensions.firstOrNull { it.run { renderRightPanel(selection) } }
                }

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
                tab("RenderSystems") {
                    renderSystemsConfig.run {
                        nonPrimaryRenderers.forEach {
                            if (ImGui.checkbox(it.javaClass.simpleName, it.enabled)) {
                                it.enabled = !it.enabled
                            }
                        }
                    }

                    ImGui.text("Primary Renderer:")
                    ImGui.text(renderSystemsConfig.primaryRenderer.javaClass.simpleName)
                    ImGui.text("Current output")
                    renderSystemsConfig.primaryRenderers.forEach {
                        if (ImGui.checkbox(it.javaClass.simpleName, primaryRenderer == it)) {
                            primaryRenderer = it
                        }
                    }
                }
                configTab(config, window)
                renderTab(this@rightPanel, gpuProfiler, renderManager)
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
