package de.hanno.hpengine.graphics.editor.panels

import de.hanno.hpengine.graphics.RenderManager
import de.hanno.hpengine.graphics.editor.*
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import imgui.ImGui
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiWindowFlags.*

private const val rightPanelFlags =
    NoCollapse or
    NoResize or
    NoTitleBar or
    HorizontalScrollbar

fun ImGuiEditor.rightPanel(
    editorConfig: EditorConfig,
    renderManager: RenderManager,
    editorExtensions: List<EditorExtension>
): Unit = layout.run {
    ImGui.setNextWindowPos(windowWidth * (1.0f - rightPanelWidthPercentage), 0f)
    ImGui.setNextWindowSize(rightPanelWidth, windowHeight)
    ImGui.getStyle().windowMenuButtonPosition = ImGuiDir.None
    ImGui.setNextWindowBgAlpha(1.0f)
    de.hanno.hpengine.graphics.imgui.dsl.ImGui.run {
        window("Right panel", rightPanelFlags) {
            tabBar("Foo") {

                tab("Selection") {
                    editorExtensions.firstOrNull { it.run { renderRightPanel(selection) } }
                }
                configTab(config, window)
                renderTab(this@rightPanel, gpuProfiler, renderManager)
                tab("Editor") {
                    if (ImGui.beginCombo("Selection Mode", editorConfig.selectionMode.toString())) {
                        SelectionMode.entries.forEach {
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
