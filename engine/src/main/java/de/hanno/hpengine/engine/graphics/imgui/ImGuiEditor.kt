package de.hanno.hpengine.engine.graphics.imgui

import com.artemis.Component
import com.artemis.World
import com.artemis.managers.TagManager
import com.artemis.utils.Bag
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.artemis.*
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.extension.SharedDepthBuffer
import de.hanno.hpengine.engine.graphics.FinalOutput
import de.hanno.hpengine.engine.graphics.GlfwWindow
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.texture.Texture
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
import imgui.type.ImInt
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.koin.core.component.get
import org.lwjgl.glfw.GLFW

class ImGuiEditor(
    private val window: GlfwWindow,
    private val gpuContext: GpuContext<OpenGl>,
    private val finalOutput: FinalOutput,
    private val sceneManager: SceneManager,
    private val config: Config,
    private val sharedDepthBuffer: SharedDepthBuffer,
) : RenderSystem {
    private var scene: Scene? = null
    private val glslVersion = "#version 450" // TODO: Derive from configured version, wikipedia OpenGl_Shading_Language
    private val renderTarget = RenderTarget(
        gpuContext,
        FrameBuffer(gpuContext, sharedDepthBuffer.depthBuffer),
        name = "Final Image",
        width = finalOutput.texture2D.dimension.width,
        height = finalOutput.texture2D.dimension.height,
        textures = listOf(finalOutput.texture2D)
    )
    private var selection: Selection? = null
    private fun selectOrUnselect(newSelection: Selection) {
        selection = if(selection == newSelection) null else newSelection
    }
    private var selectionNew: SelectionNew? = null
    private fun selectOrUnselectNew(newSelection: SelectionNew) {
        selectionNew = if(selectionNew == newSelection) null else newSelection
    }

    val output = ImInt(-1)
    val renderTargetTextures: List<Texture> get() = gpuContext.registeredRenderTargets.flatMap { it.textures }
    val currentOutputTexture: Texture get() = renderTargetTextures[output.get()]

    override lateinit var artemisWorld: World

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
        ImGui.getIO().setDisplaySize(renderTarget.width.toFloat(), renderTarget.height.toFloat())
        try {
            val screenWidth = ImGui.getIO().displaySizeX
            val screenHeight = ImGui.getIO().displaySizeY

            val leftPanelYOffset = screenHeight * 0.015f
            val leftPanelWidthPercentage = 0.1f
            val leftPanelWidth = screenWidth * leftPanelWidthPercentage

            val rightPanelWidthPercentage = 0.2f
            val rightPanelWidth = screenWidth * rightPanelWidthPercentage

            val midPanelHeight = screenHeight - leftPanelYOffset
            val midPanelWidth = screenWidth - leftPanelWidth - rightPanelWidth

            ImGui.newFrame()

            scene?.also { scene ->
                (selectionNew as? EntitySelectionNew)?.let { entitySelection ->
                    artemisWorld.getEntity(entitySelection.entity).getComponent(TransformComponent::class.java)?.let {
                        showGizmo(
                            viewMatrixAsBuffer = renderState.camera.viewMatrixAsBuffer,
                            projectionMatrixAsBuffer = renderState.camera.projectionMatrixAsBuffer,
                            fovY = renderState.camera.fov,
                            near = renderState.camera.near,
                            far = renderState.camera.far,
                            editorCameraInputSystem = scene.componentSystems.firstIsInstance<EditorCameraInputSystem>(),
                            windowWidth = screenWidth,
                            windowHeight = screenHeight,
                            panelWidth = midPanelWidth,
                            panelHeight = midPanelHeight,
                            windowPositionX = 0f,
                            windowPositionY = 0f,
                            panelPositionX = leftPanelWidth,
                            panelPositionY = leftPanelYOffset,
                            transform = it.transform,
                            viewMatrix = artemisWorld.getSystem(TagManager::class.java).getEntity(primaryCamera).getComponent(TransformComponent::class.java).transform
                        )
                    }
                }
            }

            if (output.get() != -1) {
                val windowFlags =
                    ImGuiWindowFlags.NoBringToFrontOnFocus or  // we just want to use this window as a host for the menubar and docking
                            ImGuiWindowFlags.NoNavFocus or  // so turn off everything that would make it act like a window
                            ImGuiWindowFlags.NoDocking or
                            ImGuiWindowFlags.NoTitleBar or
                            NoResize or
                            ImGuiWindowFlags.NoMove or
                            NoCollapse or
                            ImGuiWindowFlags.NoBackground
                de.hanno.hpengine.engine.graphics.imgui.dsl.ImGui.run {
                    ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
                    window("Main", windowFlags) {
                        ImGui.popStyleVar()

                        ImGui.image(currentOutputTexture.id, screenWidth, screenHeight)
                    }
                }
            }
            menu(screenWidth, screenHeight)

            leftPanel(renderState, leftPanelYOffset, leftPanelWidth, screenHeight)

            rightPanel(screenWidth, rightPanelWidthPercentage, rightPanelWidth, screenHeight)

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

    private fun menu(screenWidth: Float, screenHeight: Float) {
        // https://github-wiki-see.page/m/JeffM2501/raylibExtras/wiki/Using-ImGui-Docking-Branch-with-rlImGui
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(screenWidth, screenHeight)
        val windowFlags =
            ImGuiWindowFlags.NoBringToFrontOnFocus or  // we just want to use this window as a host for the menubar and docking
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
                                    entity("Plane") {
                                        transform.scale(20f)
                                        add(
                                            StaticModelComponentDescription(
                                                "assets/models/plane.obj",
                                                Directory.Engine,
                                            )
                                        )
                                    }
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
    }

    private fun leftPanel(renderState: RenderState, leftPanelYOffset: Float, leftPanelWidth: Float, screenHeight: Float) {
        ImGui.setNextWindowPos(0f, leftPanelYOffset)
        ImGui.setNextWindowSize(leftPanelWidth, screenHeight)
        de.hanno.hpengine.engine.graphics.imgui.dsl.ImGui.run {
            scene?.also { scene ->
                window("Scene", NoCollapse or NoResize) {
                    val componentsForEntity: Map<Int, Bag<Component>> = renderState.componentsForEntities
                    componentsForEntity.forEach { (entityIndex, components) ->
                        treeNode(
                            components.firstIsInstanceOrNull<NameComponent>()?.name ?: (artemisWorld.getSystem(TagManager::class.java).getTag(entityIndex) ?: "Entity $entityIndex")
                        ) {
                            text("Entity") {
                                selectOrUnselectNew(SimpleEntitySelectionNew(entityIndex, components.data.filterNotNull()))
                            }
                            components.forEach { component ->
                                text(component.javaClass.simpleName) {
                                    when(component) {
                                       is ModelComponent -> {
                                           selectOrUnselectNew(ModelComponentSelectionNew(entityIndex, component))
                                       }
                                        is NameComponent -> selectOrUnselectNew(NameSelectionNew(entityIndex, component.name))
                                    }
                                }
                            }
                        }
                    }
                    scene.getEntities().forEach { entity ->
                        if (!entity.hasParent) {
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
    }

    private fun rightPanel(
        screenWidth: Float,
        rightPanelWidthPercentage: Float,
        rightPanelWidth: Float,
        screenHeight: Float
    ) {
        ImGui.setNextWindowPos(screenWidth * (1.0f - rightPanelWidthPercentage), 0f)
        ImGui.setNextWindowSize(rightPanelWidth, screenHeight)
        ImGui.getStyle().windowMenuButtonPosition = ImGuiDir.None
        de.hanno.hpengine.engine.graphics.imgui.dsl.ImGui.run {
            when (val selection = selection) {
                is SimpleEntitySelection -> {
                    window(selection.entity.name, NoCollapse or NoResize) {
                        checkBox("Visible", selection.entity.visible) {
                            selection.entity.visible = it
                        }
                        val position = selection.entity.transform.position
                        text("x: ${position.x} y: ${position.y} z: ${position.z}")
                    }
                }
                else -> {
                    when(val selectionNew = selectionNew) {
                        is MeshSelectionNew -> { }
                        is ModelComponentSelectionNew -> { }
                        is ModelSelectionNew -> { }
                        is NameSelectionNew -> window(selectionNew.entity.toString(), NoCollapse or NoResize) {
                            text("Name: ${selectionNew.name}")
                        }
                        is SimpleEntitySelectionNew -> window(selectionNew.entity.toString(), NoCollapse or NoResize) {
                            val system = artemisWorld.getSystem(InvisibleComponentSystem::class.java)
                            checkBox("Visible", !system.invisibleComponentMapper.has(selectionNew.entity)) { visible ->
                                system.invisibleComponentMapper.set(selectionNew.entity, !visible)
                            }
                            selectionNew.components.firstIsInstanceOrNull<TransformComponent>()?.run {
                                val position = transform.position
                                text("x: ${position.x} y: ${position.y} z: ${position.z}")
                            }
                        }
                        is GiVolumeSelectionNew -> { }
                        is MaterialSelectionNew -> { }
                        SelectionNew.None -> { }
                        is OceanWaterSelectionNew -> { }
                        is ReflectionProbeSelectionNew -> { }
                        is SceneSelectionNew -> { }
                        null -> window("Select output", NoCollapse or NoResize) {
                            var counter = 0

                            ImGui.radioButton("Default", output, -1)
                            gpuContext.registeredRenderTargets.forEach { target ->
                                target.renderedTextures.forEachIndexed { textureIndex, texture ->
                                    ImGui.radioButton(target.name + "[$textureIndex]", output, counter)
                                    counter++
                                }
                            }
                            text("Output: " + output.get())
                        }
                    }!!
                }
            }
        }
    }
}