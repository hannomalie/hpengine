package de.hanno.hpengine.graphics.editor

import InternalTextureFormat
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GlfwWindow
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.RenderTarget
import de.hanno.hpengine.graphics.constants.MinFilter
import de.hanno.hpengine.graphics.constants.TextureFilterConfig
import de.hanno.hpengine.graphics.constants.WrapMode
import de.hanno.hpengine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.TextureDimension2D
import de.hanno.hpengine.graphics.texture.UploadInfo
import de.hanno.hpengine.ressources.FileMonitor
import de.hanno.hpengine.stopwatch.OpenGLGPUProfiler
import imgui.ImGui
import imgui.gl3.ImGuiImplGl3
import imgui.type.ImBoolean
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWWindowCloseCallbackI
import kotlin.system.exitProcess

//private val exitOnCloseCallback = GLFWWindowCloseCallbackI { handle: Long ->
//    GLFW.glfwSetWindowShouldClose(handle, true)
//}

fun main() {
    val config = Config()
    val window = GlfwWindow(
        width = 800,
        height = 600,
        config = config,
        title = "Vaanilla ImGui",
        vSync = true,
        visible = true,
//        closeCallback = exitOnCloseCallback,
        profiler = OpenGLGPUProfiler(config.profiling::profiling),
        createBackgroundContext = false,
        parentWindow = null,
    )
    val graphicsApi = OpenGLContext(window, config)

    graphicsApi.onGpu {
        ImGui.createContext()
    }

    val imGuiImplGlfw = ImGuiImplGlfwFrameBufferAware().apply {
        init(window.handle, true)
    }
    val glslVersion = "#version 450"
    val imGuiImplGl3 = graphicsApi.onGpu {
        ImGuiImplGl3().apply {
            init(glslVersion)
        }
    }

    val renderTarget = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(null),
        textures = listOf(
            graphicsApi.Texture2D(
                UploadInfo.SingleMipLevelTexture2DUploadInfo(
                    dimension = TextureDimension2D(1024, 1024),
                    internalFormat = InternalTextureFormat.RGBA16F,
                    textureFilterConfig = TextureFilterConfig(
                        minFilter = MinFilter.LINEAR_MIPMAP_LINEAR,
                    ),
                ),
                WrapMode.ClampToEdge
            )
        ),
        "Main Target",
    ).apply {
        use(false)
    }
    val renderer = SimpleTextureRenderer(
        graphicsApi,
        config,
        renderTarget.textures.first(),
        OpenGlProgramManager(graphicsApi, FileMonitor(config), config),
        window.frontBuffer
    )

    graphicsApi.perFrameAction = {
        window.pollEvents()

        renderTarget.use(true)
        graphicsApi.clearColor(1f, 0f, 0f, 1f)
        imGuiImplGlfw.newFrame(renderTarget.width, renderTarget.height)
        ImGui.newFrame()
        ImGui.showDemoWindow(ImBoolean(true))
        ImGui.render()
        imGuiImplGl3.renderDrawData(ImGui.getDrawData())
        window.frontBuffer.use(graphicsApi, true)
        renderer.render(RenderState(graphicsApi))

        window.swapBuffers()
    }
    graphicsApi.loopCondition = { !window.closeRequested.get() }
    graphicsApi.afterLoop = {
        window.close()
        exitProcess(0)
    }
}