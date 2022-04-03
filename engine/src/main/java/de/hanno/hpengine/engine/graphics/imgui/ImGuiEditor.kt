package de.hanno.hpengine.engine.graphics.imgui

import com.artemis.Component
import com.artemis.World
import com.artemis.managers.TagManager
import com.artemis.utils.Bag
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.clear
import de.hanno.hpengine.engine.component.artemis.InvisibleComponentSystem
import de.hanno.hpengine.engine.component.artemis.ModelComponent
import de.hanno.hpengine.engine.component.artemis.NameComponent
import de.hanno.hpengine.engine.component.artemis.TransformComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.extension.SharedDepthBuffer
import de.hanno.hpengine.engine.graphics.FinalOutput
import de.hanno.hpengine.engine.graphics.GlfwWindow
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.DeferredRenderExtensionConfig
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.loadDemoScene
import de.hanno.hpengine.engine.model.texture.Texture
import imgui.ImGui
import imgui.flag.*
import imgui.flag.ImGuiWindowFlags.*
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import imgui.type.ImInt
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.lwjgl.glfw.GLFW

class ImGuiEditor(
    private val window: GlfwWindow,
    private val gpuContext: GpuContext<OpenGl>,
    private val finalOutput: FinalOutput,
    private val config: ConfigImpl,
    private val sharedDepthBuffer: SharedDepthBuffer,
    private val deferredRenderExtensionConfig: DeferredRenderExtensionConfig,
    private val renderExtensions: List<DeferredRenderExtension<OpenGl>>
) : RenderSystem {
    private val glslVersion = "#version 450" // TODO: Derive from configured version, wikipedia OpenGl_Shading_Language
    private val renderTarget = RenderTarget(
        gpuContext,
        FrameBuffer(gpuContext, sharedDepthBuffer.depthBuffer),
        name = "Final Image",
        width = finalOutput.texture2D.dimension.width,
        height = finalOutput.texture2D.dimension.height,
        textures = listOf(finalOutput.texture2D)
    )
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

            (selectionNew as? EntitySelectionNew)?.let { entitySelection ->
                artemisWorld.getEntity(entitySelection.entity).getComponent(TransformComponent::class.java)?.let {
                    showGizmo(
                        viewMatrixAsBuffer = renderState.camera.viewMatrixAsBuffer,
                        projectionMatrixAsBuffer = renderState.camera.projectionMatrixAsBuffer,
                        fovY = renderState.camera.fov,
                        near = renderState.camera.near,
                        far = renderState.camera.far,
                        editorCameraInputSystem = artemisWorld.getSystem(EditorCameraInputSystem::class.java),
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
                            artemisWorld.clear()
                        }
                        menuItem("Load Demo") {
                            artemisWorld.loadDemoScene(config)
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
            window("Right panel", NoCollapse or NoResize or NoTitleBar) {
                tabBar("Foo") {

                    when(val selectionNew = selectionNew) {
                        is MeshSelectionNew -> { tab("Entity") { } }
                        is ModelComponentSelectionNew -> { tab("Entity") { } }
                        is ModelSelectionNew -> { tab("Entity") { } }
                        is NameSelectionNew -> { tab("Entity") { } }
                        is SimpleEntitySelectionNew -> tab("Entity") {
                            val system = artemisWorld.getSystem(InvisibleComponentSystem::class.java)
                            selectionNew.components.firstIsInstanceOrNull<NameComponent>()?.run {
                                text("Name: $name")
                            }
                            checkBox("Visible", !system.invisibleComponentMapper.has(selectionNew.entity)) { visible ->
                                system.invisibleComponentMapper.set(selectionNew.entity, !visible)
                            }
                            selectionNew.components.firstIsInstanceOrNull<TransformComponent>()?.run {
                                val position = transform.position
                                val positionArray = floatArrayOf(position.x, position.y, position.z)
                                ImGui.inputFloat3("Position", positionArray, "%.3f", ImGuiInputTextFlags.ReadOnly)
                            }
                        }
                        is GiVolumeSelectionNew -> { tab("Entity") { } }
                        is MaterialSelectionNew -> { tab("Entity") { } }
                        SelectionNew.None -> { tab("Entity") { } }
                        is OceanWaterSelectionNew -> { tab("Entity") { } }
                        is ReflectionProbeSelectionNew -> { tab("Entity") { } }
                        null -> { tab("Entity") { } }
                    }!!

                    tab("Output") {
                        var counter = 0
                        text("Select output")
                        ImGui.radioButton("Default", output, -1)
                        gpuContext.registeredRenderTargets.forEach { target ->
                            target.renderedTextures.forEachIndexed { textureIndex, texture ->
                                ImGui.radioButton(target.name + "[$textureIndex]", output, counter)
                                counter++
                            }
                        }
                    }
                    tab("RenderExtensions") {
                        deferredRenderExtensionConfig.run {
                            renderExtensions.forEach {
                                if(ImGui.checkbox(it.javaClass.simpleName, it.enabled)) {
                                    it.enabled = !it.enabled
                                }
                            }
                        }
                    }
                    tab("Config") {
                        if(ImGui.checkbox("Draw lines", config.debug.isDrawLines)) {
                            config.debug.isDrawLines = !config.debug.isDrawLines
                        }
                        if(ImGui.checkbox("Editor", config.debug.isEditorOverlay)) {
                            config.debug.isEditorOverlay = !config.debug.isEditorOverlay
                        }
                    }
                }
            }
        }
    }
}