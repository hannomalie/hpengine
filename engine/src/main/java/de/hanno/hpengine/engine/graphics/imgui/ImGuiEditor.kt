package de.hanno.hpengine.engine.graphics.imgui

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.GlfwWindow
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget2D
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import imgui.ImGui
import imgui.flag.*
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import imgui.type.ImBoolean
import org.lwjgl.glfw.GLFW

class ImGuiEditor(
    private val window: GlfwWindow,
    private val gpuContext: GpuContext<OpenGl>,
    private val renderTarget: RenderTarget2D
) : RenderSystem {
    private val glslVersion = "#version 450" // TODO: Derive from configured version, wikipedia OpenGl_Shading_Language

    init {
        window {
            ImGui.createContext()
            ImGui.getIO().apply {
//                addConfigFlags(ImGuiConfigFlags.ViewportsEnable)
                addConfigFlags(ImGuiConfigFlags.DockingEnable)
            }
        }
    }

    val imGuiImplGlfw = window {
        ImGuiImplGlfw().apply {
            init(window.handle, true)
        }
    }
    val imGuiImplGl3 = window {
        ImGuiImplGl3().apply {
            init(glslVersion)
        }
    }

    override fun renderEditor(result: DrawResult, renderState: RenderState) = runCatching {
        renderTarget.use(gpuContext, true)
        imGuiImplGlfw.newFrame()
        val screenWidth = ImGui.getIO().displaySizeX
        val screenHeight = ImGui.getIO().displaySizeY

        ImGui.newFrame()

        // https://github-wiki-see.page/m/JeffM2501/raylibExtras/wiki/Using-ImGui-Docking-Branch-with-rlImGui
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(screenWidth, screenHeight)
        val windowFlags = ImGuiWindowFlags.NoBringToFrontOnFocus or  // we just want to use this window as a host for the menubar and docking
                ImGuiWindowFlags.NoNavFocus or  // so turn off everything that would make it act like a window
                ImGuiWindowFlags.NoDocking or
                ImGuiWindowFlags.NoTitleBar or
                ImGuiWindowFlags.NoResize or
                ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoCollapse or
                ImGuiWindowFlags.MenuBar or
                ImGuiWindowFlags.NoBackground

        de.hanno.hpengine.engine.graphics.imgui.dsl.ImGui.run {
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
            window("Main", windowFlags) {
                ImGui.popStyleVar()
                menuBar {
                    menu("File") {
                        menuItem("New Scene") {
                            println("Creating new scene")
                        }
                    }
                }
            }
        }

        ImGui.setNextWindowPos(0f, screenHeight * 0.02f)
        ImGui.setNextWindowSize(screenWidth * 0.1f, screenHeight)
        ImGui.getStyle().windowMenuButtonPosition = ImGuiDir.None

        de.hanno.hpengine.engine.graphics.imgui.dsl.ImGui.run {
            window("Scene", ImGuiWindowFlags.NoCollapse) {
                treeNode("Scene") {
                    treeNode("Foo") {
                        text("asdasd")
                    }
                    treeNode("Bar") {
                        text("asdasd")
                    }
                }
            }
        }
        ImGui.showDemoWindow(ImBoolean(true))
        ImGui.render()
        imGuiImplGl3.renderDrawData(ImGui.getDrawData())
        if(ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            val backupWindowHandle = GLFW.glfwGetCurrentContext()
            ImGui.updatePlatformWindows()
            ImGui.renderPlatformWindowsDefault()
            GLFW.glfwMakeContextCurrent(backupWindowHandle)
        }
        Unit
    }.getOrElse {
        it.printStackTrace()
    }
}