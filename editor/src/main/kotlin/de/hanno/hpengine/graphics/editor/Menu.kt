package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.clear
import de.hanno.hpengine.loadDemoScene
import imgui.ImGui
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags

fun ImGuiEditor.menu(screenWidth: Float, screenHeight: Float) {
    // https://github-wiki-see.page/m/JeffM2501/raylibExtras/wiki/Using-ImGui-Docking-Branch-with-rlImGui
    ImGui.setNextWindowPos(0f, 0f)
    ImGui.setNextWindowSize(screenWidth, screenHeight)
    val windowFlags =
        ImGuiWindowFlags.NoBringToFrontOnFocus or  // we just want to use this window as a host for the menubar and docking
                ImGuiWindowFlags.NoNavFocus or  // so turn off everything that would make it act like a window
//                    NoDocking or
                ImGuiWindowFlags.NoTitleBar or
                ImGuiWindowFlags.NoResize or
                ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoCollapse or
                ImGuiWindowFlags.MenuBar or
                ImGuiWindowFlags.NoBackground
    de.hanno.hpengine.graphics.imgui.dsl.ImGui.run {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        window("Main", windowFlags) {
            ImGui.popStyleVar()

            menuBar {
                menu("File") {
                    menuItem("New Scene") {
                        addResourceContext.launch {
                            artemisWorld.clear()
                        }
                    }
                    menuItem("Load Demo") {
                        addResourceContext.launch {
                            artemisWorld.loadDemoScene()
                        }
                    }
                }
            }
        }
    }
}


