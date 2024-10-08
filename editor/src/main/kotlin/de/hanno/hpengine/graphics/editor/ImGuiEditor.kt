package de.hanno.hpengine.graphics.editor

import InternalTextureFormat.RGB8
import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentManager
import com.artemis.World
import com.artemis.annotations.All
import com.artemis.utils.Bag
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.*
import de.hanno.hpengine.graphics.constants.Facing
import de.hanno.hpengine.graphics.constants.MinFilter
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.editor.panels.PanelLayout
import de.hanno.hpengine.graphics.editor.panels.leftPanel
import de.hanno.hpengine.graphics.editor.panels.rightPanel
import de.hanno.hpengine.graphics.editor.select.EntitySelection
import de.hanno.hpengine.graphics.editor.select.MeshSelection
import de.hanno.hpengine.graphics.editor.select.Selection
import de.hanno.hpengine.graphics.editor.select.SimpleEntitySelection
import de.hanno.hpengine.graphics.fps.FPSCounter
import de.hanno.hpengine.graphics.output.FinalOutput
import de.hanno.hpengine.graphics.output.FinalOutputImpl
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.rendertarget.*
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.model.BoundingVolumeComponent
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.model.ModelSystem
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.EntityMovementSystem
import imgui.ImGui
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags.*
import imgui.gl3.ImGuiImplGl3
import org.koin.core.annotation.Single
import org.lwjgl.glfw.GLFW

interface ImGuiEditorExtension {
    fun render(imGuiEditor: ImGuiEditor)
}

enum class SelectionMode { Entity, Mesh; }
data class EditorConfig(var selectionMode: SelectionMode = SelectionMode.Entity)

@Single(binds = [BaseSystem::class, BaseEntitySystem::class, RenderSystem::class])
@All(EditorCameraInputComponent::class)
class ImGuiEditor(
    private val graphicsApi: GraphicsApi,
    internal val window: Window,
    internal val textureManager: TextureManagerBaseSystem,
    internal val config: Config,
    internal val addResourceContext: AddResourceContext,
    internal val fpsCounter: FPSCounter,
    internal val editorExtensions: List<ImGuiEditorExtension>,
    internal val entityClickListener: EntityClickListener,
    internal val primaryCameraStateHolder: PrimaryCameraStateHolder,
    internal val gpuProfiler: GPUProfiler,
    internal val renderSystemsConfig: Lazy<RenderSystemsConfig>,
    internal val renderManager: Lazy<RenderManager>,
    internal val programManager: ProgramManager,
    internal val input: EditorInput,
    internal val primaryRendererSelection: PrimaryRendererSelection,
    internal val outputSelection: OutputSelection,
    internal val entityMovementSystem: EntityMovementSystem,
    _editorExtensions: List<EditorExtension>,
) : BaseEntitySystem(), PrimaryRenderer {
    override val renderPriority: Int get() = 100

    private val glslVersion = "#version 450" // TODO: Derive from configured version, wikipedia OpenGl_Shading_Language
    override val supportsSingleStep: Boolean get() = false
    internal val extensions: List<EditorExtension> = _editorExtensions.distinct()

    private var editorConfig = EditorConfig()

    internal val layout = PanelLayout()

    private val gizmoSystem = GizmoSystem(input)

    val renderTarget = RenderTarget2D(
        graphicsApi,
        RenderTargetImpl(
            graphicsApi,
            OpenGLFrameBuffer(graphicsApi, null),
            1920,
            1080,
            listOf(
                ColorAttachmentDefinition("Color", RGB8, TextureFilterConfig(MinFilter.LINEAR))
            ).toTextures(graphicsApi, config.width, config.height),
            "Final Editor Image",
        )
    )
    override val finalOutput: FinalOutput = FinalOutputImpl(renderTarget.textures.first(), 0, this)

    var selection: Selection? = null
        set(value) {
            field = if (field == value) null else value
        }

    lateinit var artemisWorld: World

    init {
        graphicsApi.onGpu {
            ImGui.createContext()
            ImGui.getIO().apply {
//                addConfigFlags(ImGuiConfigFlags.ViewportsEnable)
                addConfigFlags(ImGuiConfigFlags.DockingEnable)
            }
        }
    }

    private val imGuiImplGlfw = graphicsApi.onGpu {
        ImGuiImplGlfwFrameBufferAware().apply {
            init(window.handle, true)
        }
    }
    private val imGuiImplGl3 = graphicsApi.onGpu {
        ImGuiImplGl3().apply {
            init(glslVersion)
        }
    }

    override fun processSystem() {}
    override fun render(renderState: RenderState) {
        primaryRendererSelection.primaryRenderer.render(renderState)

        layout.update(ImGui.getIO().displaySizeX, ImGui.getIO().displaySizeY)

        entityClickListener.consumeClick { entityClicked -> handleClick(entityClicked) }

        graphicsApi.polygonMode(Facing.FrontAndBack, RenderingMode.Fill)

        outputSelection.draw()

        renderTarget.use(true)
        imGuiImplGlfw.newFrame(renderTarget.width, renderTarget.height)

        try {

            ImGui.newFrame()

            background(layout.windowWidth, layout.windowHeight)

            if (renderPanels) {
                menu(layout.windowWidth, layout.windowHeight)
                rightPanel(editorConfig, renderManager.value, extensions)

                if (::artemisWorld.isInitialized) {
                    leftPanel(layout.leftPanelYOffset, layout.leftPanelWidth, layout.windowHeight)
                }
                midPanel(renderState)

                bottomPanel()
            }


            renderEditorExtensions()
//            ImGui.showDemoWindow(ImBoolean(true))
        } catch (it: Exception) {
            it.printStackTrace()
        } finally {
            try {

                try {
                    ImGui.render()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                imGuiImplGl3.renderDrawData(ImGui.getDrawData())
                if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                    val backupWindowHandle = GLFW.glfwGetCurrentContext()
                    ImGui.updatePlatformWindows()
                    ImGui.renderPlatformWindowsDefault()
                    GLFW.glfwMakeContextCurrent(backupWindowHandle)
                }
            } catch (it: Exception) {
                it.printStackTrace()
            }
        }
    }

    private fun renderEditorExtensions() {
        editorExtensions.forEach {
            try {
                it.render(this)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClick(entityClicked: EntityClicked) {
        if (layout.contains(entityClicked)) {
            val entityId = entityClicked.indices.entityId
            val meshIndex = entityClicked.indices.meshIndex
            val componentManager = artemisWorld.getSystem(ComponentManager::class.java)!!
            val components = componentManager.getComponentsFor(entityId, Bag())
            selection = when (editorConfig.selectionMode) {
                SelectionMode.Mesh -> {
                    val modelComponent = components.filterIsInstance<ModelComponent>().first()
                    val model =
                        artemisWorld.getSystem(ModelSystem::class.java)!![modelComponent.modelComponentDescription]!!
                    MeshSelection(entityId, model.meshes[meshIndex], modelComponent, components)
                }

                else -> SimpleEntitySelection(entityId, components)
            }
        }
    }

    private fun bottomPanel() {
        (selection as? EntitySelection)?.let {
            ImGui.setNextWindowPos(layout.bottomPanelPositionX, layout.bottomPanelPositionY)
            ImGui.setNextWindowSize(layout.bottomPanelWidth, layout.bottomPanelHeight)
            ImGui.getStyle().windowMenuButtonPosition = ImGuiDir.None
            ImGui.setNextWindowBgAlpha(1.0f)

            de.hanno.hpengine.graphics.imgui.dsl.ImGui.run {
                window(
                    "Bottom panel", NoCollapse or
                            NoResize or
                            NoTitleBar or
                            HorizontalScrollbar
                ) {
                    gizmoSystem.renderTransformationConfig()
                }
            }
        }
    }

    private val renderPanels get() = !input.prioritizeGameInput

    private fun background(screenWidth: Float, screenHeight: Float) {
        val windowFlags =
            NoBringToFrontOnFocus or  // we just want to use this window as a host for the menubar and docking
                    NoNavFocus or  // so turn off everything that would make it act like a window
                    //                            NoDocking or
                    NoTitleBar or
                    NoResize or
                    NoMove or
                    NoCollapse or
                    NoBackground
        de.hanno.hpengine.graphics.imgui.dsl.ImGui.run {
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
            window("Main", windowFlags) {
                ImGui.popStyleVar()

                ImGui.image(
                    outputSelection.textureOrNull ?: primaryRendererSelection.primaryRenderer.finalOutput?.texture2D?.id ?: 0,
                    screenWidth,
                    screenHeight,
                    0f,
                    1f,
                    1f,
                    0f,
                )

                input.swallowInput = !ImGui.isItemHovered()
            }
        }
    }

    private fun ImGuiEditor.midPanel(renderState: RenderState) {
        (selection as? EntitySelection)?.let { entitySelection ->
            val entity = artemisWorld.getEntity(entitySelection.entity)
            entity.getComponent(TransformComponent::class.java)
                ?.let { transformComponent ->
                    val camera = renderState[primaryCameraStateHolder.camera]
                    val entityMoved = gizmoSystem.showGizmo(
                        viewMatrixBuffer = camera.viewMatrixBuffer,
                        projectionMatrixBuffer = camera.projectionMatrixBuffer,
                        panelLayout = layout,
                        transform = transformComponent.transform,
                        entity.getComponent(BoundingVolumeComponent::class.java)?.boundingVolume ?: dummyAABB
                    )
                    if(entityMoved) {
                        entityMovementSystem.setEntityHasMovedInCycle(entitySelection.entity, -1)
                    }
                }
        }
    }
}

private val dummyAABB = AABB()

private fun PanelLayout.contains(entityClicked: EntityClicked): Boolean {
    return entityClicked.coordinates.x > leftPanelWidth &&
            entityClicked.coordinates.x < (leftPanelWidth + midPanelWidth) &&
            entityClicked.coordinates.y > 0 &&
            windowHeight - entityClicked.coordinates.y < bottomPanelPositionY
}
