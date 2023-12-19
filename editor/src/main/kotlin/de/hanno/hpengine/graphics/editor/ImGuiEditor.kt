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
import de.hanno.hpengine.graphics.editor.select.EntitySelection
import de.hanno.hpengine.graphics.editor.select.MeshSelection
import de.hanno.hpengine.graphics.editor.select.Selection
import de.hanno.hpengine.graphics.editor.select.SimpleEntitySelection
import de.hanno.hpengine.graphics.fps.FPSCounter
import de.hanno.hpengine.graphics.output.FinalOutput
import de.hanno.hpengine.graphics.output.FinalOutputImpl
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.rendertarget.OpenGLFrameBuffer
import de.hanno.hpengine.graphics.rendertarget.RenderTarget2D
import de.hanno.hpengine.graphics.rendertarget.toTextures
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.model.ModelSystem
import de.hanno.hpengine.scene.AddResourceContext
import imgui.ImGui
import imgui.flag.ImGuiConfigFlags
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
    _editorExtensions: List<EditorExtension>,
) : BaseEntitySystem(), PrimaryRenderer {
    override val renderPriority: Int get() = 100

    private val glslVersion = "#version 450" // TODO: Derive from configured version, wikipedia OpenGl_Shading_Language
    override val supportsSingleStep: Boolean get() = false
    internal val extensions: List<EditorExtension> = _editorExtensions.distinct()

    private var editorConfig = EditorConfig()

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
        if (!config.debug.isEditorOverlay) return

        val screenWidth = ImGui.getIO().displaySizeX
        val screenHeight = ImGui.getIO().displaySizeY

        val leftPanelYOffset = 0f
        val leftPanelWidthPercentage = 0.1f
        val leftPanelWidth = screenWidth * leftPanelWidthPercentage

        val rightPanelWidthPercentage = 0.2f
        val rightPanelWidth = screenWidth * rightPanelWidthPercentage

        val midPanelHeight = screenHeight - leftPanelYOffset
        val midPanelWidth = screenWidth - leftPanelWidth - rightPanelWidth


        entityClickListener.consumeClick { entityClicked ->
            if (
                entityClicked.coordinates.x > leftPanelWidth &&
                entityClicked.coordinates.x < (leftPanelWidth + midPanelWidth)
            ) {
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
        graphicsApi.polygonMode(Facing.FrontAndBack, RenderingMode.Fill)

        outputSelection.draw()

        renderTarget.use(true)
        imGuiImplGlfw.newFrame(renderTarget.width, renderTarget.height)

        try {

            ImGui.newFrame()

            if(renderPanels) {
                (selection as? EntitySelection)?.let { entitySelection ->
                    artemisWorld.getEntity(entitySelection.entity).getComponent(TransformComponent::class.java)?.let {
                        val camera = renderState[primaryCameraStateHolder.camera]
                        showGizmo(
                            viewMatrixAsBuffer = camera.viewMatrixBuffer,
                            projectionMatrixAsBuffer = camera.projectionMatrixBuffer,
                            editorCameraInputSystem = artemisWorld.getSystem(EditorCameraInputSystem::class.java),
                            windowWidth = screenWidth,
                            windowHeight = screenHeight,
                            panelWidth = midPanelWidth,
                            panelHeight = midPanelHeight,
                            windowPositionX = 0f,
                            windowPositionY = 0f,
                            panelPositionX = leftPanelWidth,
                            panelPositionY = leftPanelYOffset,
                            transform = it.transform
                        )
                    }
                }
            }

            background(screenWidth, screenHeight)

            if(renderPanels) {
                menu(screenWidth, screenHeight)
            }

            if (::artemisWorld.isInitialized && renderPanels) {
                leftPanel(leftPanelYOffset, leftPanelWidth, screenHeight)
            }

            if(renderPanels) {
                graphicsApi.run {
                    profiled("rightPanel") {
                        rightPanel(
                            screenWidth,
                            rightPanelWidth,
                            screenHeight,
                            editorConfig,
                            renderSystemsConfig.value,
                            renderManager.value,
                            extensions
                        )
                    }
                }
            }

            editorExtensions.forEach {
                try {
                    it.render(this)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
//            ImGui.showDemoWindow(ImBoolean(true))
        } catch (it: Exception) {
            it.printStackTrace()
        } finally {
            ImGui.render()
            imGuiImplGl3.renderDrawData(ImGui.getDrawData())
            if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                val backupWindowHandle = GLFW.glfwGetCurrentContext()
                ImGui.updatePlatformWindows()
                ImGui.renderPlatformWindowsDefault()
                GLFW.glfwMakeContextCurrent(backupWindowHandle)
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
                    outputSelection.textureOrNull ?: primaryRendererSelection.primaryRenderer.finalOutput.texture2D.id,
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
}
