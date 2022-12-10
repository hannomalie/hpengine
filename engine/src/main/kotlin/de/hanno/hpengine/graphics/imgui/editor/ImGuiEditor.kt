package de.hanno.hpengine.graphics.imgui.editor

import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.ComponentManager
import com.artemis.World
import com.artemis.managers.TagManager
import com.artemis.utils.Bag
import de.hanno.hpengine.artemis.ModelComponent
import de.hanno.hpengine.artemis.ModelSystem
import de.hanno.hpengine.artemis.PrimaryCameraStateHolder
import de.hanno.hpengine.artemis.TransformComponent

import de.hanno.hpengine.config.ConfigImpl
import de.hanno.hpengine.extension.SharedDepthBuffer
import de.hanno.hpengine.graphics.*
import de.hanno.hpengine.graphics.renderer.DeferredRenderExtensionConfig
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.graphics.fps.FPSCounter
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.Indices
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.OnClickListener
import de.hanno.hpengine.graphics.renderer.rendertarget.*
import de.hanno.hpengine.graphics.texture.Texture2D
import imgui.ImGui
import imgui.flag.*
import imgui.flag.ImGuiWindowFlags.*
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import imgui.type.ImInt
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.joml.Vector2i
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30

interface ImGuiEditorExtension {
    fun render(imGuiEditor: ImGuiEditor)
}

data class EntityClicked(val coordinates: Vector2i, val indices: Indices)

class EntityClickListener : OnClickListener {
    var clickState: EntityClicked? = null
    override fun onClick(coordinates: Vector2i, indices: Indices) = indices.run {
        if (clickState == null) {
            clickState = EntityClicked(coordinates, indices)
        }
    }

    inline fun <T> consumeClick(onClick: (EntityClicked) -> T): T? = clickState?.let {
        try {
            onClick(it)
        } finally {
            this.clickState = null
        }
    }
}

enum class SelectionMode { Entity, Mesh; }
data class EditorConfig(var selectionMode: SelectionMode = SelectionMode.Entity)
context(GpuContext, RenderStateContext)
class ImGuiEditor(
    internal val window: GlfwWindow,
    internal val textureManager: OpenGLTextureManager,
    internal val finalOutput: FinalOutput,
    internal val debugOutput: DebugOutput,
    internal val config: ConfigImpl,
    internal val sharedDepthBuffer: SharedDepthBuffer,
    internal val deferredRenderExtensionConfig: DeferredRenderExtensionConfig,
    internal val addResourceContext: AddResourceContext,
    internal val fpsCounter: FPSCounter,
    internal val editorExtensions: List<ImGuiEditorExtension>,
    internal val entityClickListener: EntityClickListener,
    internal val primaryCameraStateHolder: PrimaryCameraStateHolder,
) : BaseSystem(), RenderSystem {
    private val glslVersion = "#version 450" // TODO: Derive from configured version, wikipedia OpenGl_Shading_Language
    val formerFinalOutput = finalOutput.texture2D

    private var editorConfig = EditorConfig()

    val renderTarget = RenderTarget2D(
        RenderTargetImpl(
            OpenGLFrameBuffer(null),
            config.width,
            config.height,
            listOf(
                ColorAttachmentDefinition("Color", GL30.GL_RGBA32F)
            ).toTextures(config.width, config.height),
            "Final Editor Image",
        )
    ).apply {
        finalOutput.texture2D = textures[0]
    }
    var selection: Selection? = null
    fun selectOrUnselect(newSelection: Selection) {
        selection = if (selection == newSelection) null else newSelection
    }

    val output = ImInt(-1)

    data class TextureOutputSelection(val identifier: String, val texture: Texture2D)

    val textureOutputOptions: List<TextureOutputSelection>
        get() = registeredRenderTargets.flatMap { target ->
            target.textures.filterIsInstance<Texture2D>().mapIndexed { index, texture ->
                TextureOutputSelection(target.name + "[$index]", texture)
            }
        } +
                textureManager.texturesForDebugOutput.filterValues { it is Texture2D }
                    .map { TextureOutputSelection(it.key, it.value as Texture2D) } +
                textureManager.textures.filterValues { it is Texture2D }
                    .map { TextureOutputSelection(it.key, it.value as Texture2D) }

    val currentOutputTexture: TextureOutputSelection get() = textureOutputOptions[output.get()]

    val fillBag = Bag<Component>()

    lateinit var artemisWorld: World

    init {
        onGpu {
            ImGui.createContext()
            ImGui.getIO().apply {
//                addConfigFlags(ImGuiConfigFlags.ViewportsEnable)
                addConfigFlags(ImGuiConfigFlags.DockingEnable)
            }
        }
    }

    private val imGuiImplGlfw = onGpu {
        ImGuiImplGlfw().apply {
            init(window.handle, true)
        }
    }
    private val imGuiImplGl3 = onGpu {
        ImGuiImplGl3().apply {
            init(glslVersion)
        }
    }

    override fun processSystem() { }
    override fun renderEditor(renderState: RenderState) {
        if (!config.debug.isEditorOverlay) return

        val screenWidth = ImGui.getIO().displaySizeX
        val screenHeight = ImGui.getIO().displaySizeY

        val leftPanelYOffset = screenHeight * 0.015f
        val leftPanelWidthPercentage = 0.1f
        val leftPanelWidth = screenWidth * leftPanelWidthPercentage

        val rightPanelWidthPercentage = 0.2f
        val rightPanelWidth = screenWidth * rightPanelWidthPercentage

        val midPanelHeight = screenHeight - leftPanelYOffset
        val midPanelWidth = screenWidth - leftPanelWidth - rightPanelWidth


        entityClickListener.consumeClick { entityClicked ->
            if(
                entityClicked.coordinates.x > leftPanelWidth &&
                entityClicked.coordinates.x < (leftPanelWidth + midPanelWidth)
            ) {
                val entityId = entityClicked.indices.entityId
                val meshIndex = entityClicked.indices.meshIndex
                val componentManager = artemisWorld.getSystem(ComponentManager::class.java)!!
                val components = componentManager.getComponentsFor(entityId, Bag())
                selection = when (editorConfig.selectionMode) {
                    SelectionMode.Mesh -> {
                        val modelComponent = components.firstIsInstance<ModelComponent>()
                        val model =
                            artemisWorld.getSystem(ModelSystem::class.java)!![modelComponent.modelComponentDescription]!!
                        MeshSelection(entityId, model.meshes[meshIndex], modelComponent, components.toList())
                    }
                    else -> SimpleEntitySelection(entityId, components.toList())
                }
            }
        }
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
        renderTarget.use(true)
        imGuiImplGlfw.newFrame()
        ImGui.getIO().setDisplaySize(renderTarget.width.toFloat(), renderTarget.height.toFloat())
        try {

            ImGui.newFrame()

            (selection as? EntitySelection)?.let { entitySelection ->
                artemisWorld.getEntity(entitySelection.entity).getComponent(TransformComponent::class.java)?.let {
                    val camera = renderState[primaryCameraStateHolder.camera]
                    showGizmo(
                        viewMatrixAsBuffer = camera.viewMatrixAsBuffer,
                        projectionMatrixAsBuffer = camera.projectionMatrixAsBuffer,
                        fovY = camera.fov,
                        near = camera.near,
                        far = camera.far,
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
                        viewMatrix = artemisWorld.getSystem(TagManager::class.java).getEntity(primaryCamera)
                            .getComponent(TransformComponent::class.java).transform
                    )
                }
            }

            background(screenWidth, screenHeight)
            menu(screenWidth, screenHeight)

            if (::artemisWorld.isInitialized) {
                leftPanel(leftPanelYOffset, leftPanelWidth, screenHeight)
            }

            rightPanel(screenWidth, rightPanelWidth, screenHeight, editorConfig)

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

                val textureToDraw = if (output.get() > -1) {
                    textureOutputOptions[output.get()].texture.id
                } else {
                    formerFinalOutput.id
                }
                ImGui.image(
                    textureToDraw,
                    screenWidth,
                    screenHeight,
                    0f,
                    1f,
                    1f,
                    0f,
                )
            }
        }
    }
}
