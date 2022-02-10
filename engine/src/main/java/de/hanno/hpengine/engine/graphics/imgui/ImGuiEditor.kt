package de.hanno.hpengine.engine.graphics.imgui

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.FinalOutput
import de.hanno.hpengine.engine.graphics.GlfwWindow
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.scene.dsl.*
import imgui.ImGui
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.flag.ImGuiWindowFlags.NoCollapse
import imgui.flag.ImGuiWindowFlags.NoResize
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.koin.core.component.get
import org.lwjgl.glfw.GLFW

class ImGuiEditor(
    private val window: GlfwWindow,
    private val gpuContext: GpuContext<OpenGl>,
    private val finalOutput: FinalOutput,
    private val sceneManager: SceneManager,
    private val config: Config,
) : RenderSystem {
    private var scene: Scene? = null
    private val glslVersion = "#version 450" // TODO: Derive from configured version, wikipedia OpenGl_Shading_Language
    private val renderTarget = RenderTarget(
        gpuContext,
        FrameBuffer(gpuContext, null),
        name = "Final Image",
        width = finalOutput.texture2D.dimension.width,
        height = finalOutput.texture2D.dimension.height,
        textures = listOf(finalOutput.texture2D)
    )
    private var selection: Selection? = null
    private fun selectOrUnselect(newSelection: Selection) {
        selection = if(selection == newSelection) null else newSelection
    }

    init {
        window {
            ImGui.createContext()
            ImGui.getIO().apply {
//                addConfigFlags(ImGuiConfigFlags.ViewportsEnable)
                addConfigFlags(ImGuiConfigFlags.DockingEnable)
            }
        }
    }

    private val imGuiImplGlfw = window {
        ImGuiImplGlfw().apply {
            init(window.handle, true)
        }
    }
    private val imGuiImplGl3 = window {
        ImGuiImplGl3().apply {
            init(glslVersion)
        }
    }

    override fun beforeSetScene(nextScene: Scene) {
        scene = nextScene
    }
    override fun renderEditor(result: DrawResult, renderState: RenderState) {
        if(!config.debug.isEditorOverlay)  return

        renderTarget.use(gpuContext, false)
        imGuiImplGlfw.newFrame()
        try {
            val screenWidth = ImGui.getMainViewport().sizeX
            val screenHeight = ImGui.getMainViewport().sizeY

            val leftPanelYOffset = screenHeight * 0.015f
            val leftPanelWidth = screenWidth * 0.1f
            val rightPanelWidthPercentage = 0.2f
            val rightPanelWidth = screenWidth * rightPanelWidthPercentage
            val midPanelHeight = screenWidth - leftPanelYOffset
            val midPanelWidth = screenWidth - leftPanelWidth - rightPanelWidth

            ImGui.newFrame()

            scene?.also { scene ->
                showGizmo(
                    renderState.camera.viewMatrixAsBuffer,
                    renderState.camera.projectionMatrixAsBuffer,
                    selection as? SimpleEntitySelection,
                    scene.componentSystems.firstIsInstance<EditorCameraInputSystem>(),
                    midPanelWidth,
                    midPanelHeight,
                    leftPanelWidth,
                    leftPanelYOffset,
                )
            }

            // https://github-wiki-see.page/m/JeffM2501/raylibExtras/wiki/Using-ImGui-Docking-Branch-with-rlImGui
            ImGui.setNextWindowPos(0f, 0f)
            ImGui.setNextWindowSize(screenWidth, screenHeight)
            val windowFlags = ImGuiWindowFlags.NoBringToFrontOnFocus or  // we just want to use this window as a host for the menubar and docking
                    ImGuiWindowFlags.NoNavFocus or  // so turn off everything that would make it act like a window
                    ImGuiWindowFlags.NoDocking or
                    ImGuiWindowFlags.NoTitleBar or
                    NoResize or
                    ImGuiWindowFlags.NoMove or
                    NoCollapse or
                    ImGuiWindowFlags.MenuBar or
                    ImGuiWindowFlags.NoBackground
            de.hanno.hpengine.engine.graphics.imgui.dsl.ImGui.run {
                ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
                window("Main", windowFlags) {
                    ImGui.popStyleVar()
                    menuBar {
                        menu("File") {
                            menuItem("New Scene") {
                                GlobalScope.launch {
                                    sceneManager.scene = SceneDescription("Scene_${System.currentTimeMillis()}").convert(
                                        sceneManager.scene.get(),
                                        sceneManager.scene.get()
                                    )
                                }
                            }
                            menuItem("Load Demo") {
                                GlobalScope.launch {
                                    sceneManager.scene = scene("Demo") {
                                        entity("Box") {
                                            add(
                                                StaticModelComponentDescription(
                                                    "assets/models/cube.obj",
                                                    Directory.Engine,
                                                )
                                            )
                                        }
                                    }.convert(
                                        sceneManager.scene.get(),
                                        sceneManager.scene.get()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ImGui.setNextWindowPos(0f, leftPanelYOffset)
            ImGui.setNextWindowSize(leftPanelWidth, screenHeight)
            de.hanno.hpengine.engine.graphics.imgui.dsl.ImGui.run {
                scene?.also { scene ->
                    window("Scene", NoCollapse or NoResize) {
                        scene.getEntities().forEach { entity ->
                            if(!entity.hasParent) {
                                treeNode(entity.name) {
                                    entity.children.forEach { child ->
                                        treeNode(child.name) {
                                            text("Entity") {
                                                selectOrUnselect(SimpleEntitySelection(child))
                                            }
                                        }
                                    }
                                    text("Entity") {
                                        selectOrUnselect(SimpleEntitySelection(entity))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ImGui.setNextWindowPos(screenWidth * (1.0f - rightPanelWidthPercentage), 0f)
            ImGui.setNextWindowSize(rightPanelWidth, screenHeight)
            ImGui.getStyle().windowMenuButtonPosition = ImGuiDir.None
            de.hanno.hpengine.engine.graphics.imgui.dsl.ImGui.run {
                when(val selection = selection) {
                    is SimpleEntitySelection -> {
                        window(selection.entity.name, NoCollapse or NoResize) {
                            checkBox("Visible", selection.entity.visible) {
                                selection.entity.visible = it
                            }
                        }
                    }
                }
            }
//            ImGui.showDemoWindow(ImBoolean(true))
        } catch (it: Exception) {
            it.printStackTrace()
        } finally {
            ImGui.render()
            imGuiImplGl3.renderDrawData(ImGui.getDrawData())
            if(ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                val backupWindowHandle = GLFW.glfwGetCurrentContext()
                ImGui.updatePlatformWindows()
                ImGui.renderPlatformWindowsDefault()
                GLFW.glfwMakeContextCurrent(backupWindowHandle)
            }
        }
    }
}