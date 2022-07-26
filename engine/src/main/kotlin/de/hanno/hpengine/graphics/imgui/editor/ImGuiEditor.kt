package de.hanno.hpengine.graphics.imgui.editor

import com.artemis.Component
import com.artemis.World
import com.artemis.managers.TagManager
import com.artemis.utils.Bag
import de.hanno.hpengine.artemis.TransformComponent
import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.config.ConfigImpl
import de.hanno.hpengine.extension.SharedDepthBuffer
import de.hanno.hpengine.graphics.renderer.DeferredRenderExtensionConfig
import de.hanno.hpengine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.model.texture.Texture
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.graphics.fps.FPSCounter
import de.hanno.hpengine.graphics.DebugOutput
import de.hanno.hpengine.graphics.FinalOutput
import de.hanno.hpengine.graphics.GlfwWindow
import de.hanno.hpengine.graphics.GpuContext
import imgui.ImGui
import imgui.flag.*
import imgui.flag.ImGuiWindowFlags.*
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import imgui.type.ImInt
import org.lwjgl.glfw.GLFW

interface ImGuiEditorExtension {
    fun render(imGuiEditor: ImGuiEditor)
}
class ImGuiEditor(
    internal val window: GlfwWindow,
    internal val gpuContext: GpuContext<OpenGl>,
    internal val textureManager: TextureManager,
    internal val finalOutput: FinalOutput,
    internal val debugOutput: DebugOutput,
    internal val config: ConfigImpl,
    internal val sharedDepthBuffer: SharedDepthBuffer,
    internal val deferredRenderExtensionConfig: DeferredRenderExtensionConfig,
    internal val renderExtensions: List<DeferredRenderExtension<OpenGl>>,
    internal val addResourceContext: AddResourceContext,
    internal val fpsCounter: FPSCounter,
    internal val editorExtensions: List<ImGuiEditorExtension>,
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
    var selection: Selection? = null
    fun selectOrUnselect(newSelection: Selection) {
        selection = if (selection == newSelection) null else newSelection
    }

    val output = ImInt(-1)
    val renderTargetTextures: List<Texture> get() = gpuContext.registeredRenderTargets.flatMap { it.textures } + textureManager.texturesForDebugOutput.values
    val currentOutputTexture: Texture get() = renderTargetTextures[output.get()]

    val fillBag = Bag<Component>()

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
        if (!config.debug.isEditorOverlay) return

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

            (selection as? EntitySelection)?.let { entitySelection ->
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
                        viewMatrix = artemisWorld.getSystem(TagManager::class.java).getEntity(primaryCamera)
                            .getComponent(TransformComponent::class.java).transform
                    )
                }
            }

            if (output.get() != -1) {
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

                        ImGui.image(finalOutput.texture2D.id, screenWidth, screenHeight)
                    }
                }
            }
            menu(screenWidth, screenHeight)


            if(::artemisWorld.isInitialized) {
                leftPanel(leftPanelYOffset, leftPanelWidth, screenHeight)
            }

            rightPanel(screenWidth, rightPanelWidth, screenHeight)

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

}
