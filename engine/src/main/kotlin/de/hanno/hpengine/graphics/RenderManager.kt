package de.hanno.hpengine.graphics

import com.artemis.BaseSystem

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.imgui.editor.ImGuiEditor
import de.hanno.hpengine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderStateRecorder
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.launchEndlessRenderLoop
import de.hanno.hpengine.graphics.fps.FPSCounter
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.stopwatch.GPUProfiler
import org.lwjgl.opengl.GL11
import java.util.concurrent.atomic.AtomicBoolean

data class FinalOutput(var texture2D: Texture2D, var mipmapLevel: Int = 0)
data class DebugOutput(var texture2D: Texture2D? = null, var mipmapLevel: Int = 0)

context(GpuContext)
class RenderManager(
    val config: Config,
    val input: Input,
    val window: Window,
    val programManager: ProgramManager,
    val renderStateContext: RenderStateContext,
    val finalOutput: FinalOutput,
    val debugOutput: DebugOutput,
    val fpsCounter: FPSCounter,
    val renderSystemsConfig: RenderSystemsConfig,
    _renderSystems: List<RenderSystem>,
) : BaseSystem() {

    private val drawToQuadProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/fullscreen_quad_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/simpletexture_fragment.glsl"))
    )
    private val drawToDebugQuadProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/quarterscreen_quad_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/simpletexture_fragment.glsl"))
    )
    var renderMode: RenderMode = RenderMode.Normal
    // TODO: Make this read only again
    var renderSystems: MutableList<RenderSystem> = _renderSystems.distinct().toMutableList()

    private val textureRenderer = SimpleTextureRenderer(
        this@GpuContext,
        config,
        finalOutput.texture2D,
        programManager,
        window.frontBuffer
    )

    fun finishCycle(deltaSeconds: Float) {
        renderStateContext.renderState.currentWriteState.deltaSeconds = deltaSeconds
        renderStateContext.renderState.swapStaging()
    }

    internal val rendering = AtomicBoolean(false)
    init {
        launchEndlessRenderLoop { deltaSeconds ->
            onGpu(block = {
                rendering.getAndSet(true)
                try {
                    renderStateContext.renderState.readLocked { currentReadState ->

                        val renderSystems = when (val renderMode = renderMode) {
                            RenderMode.Normal -> {
                                renderSystems.filter {
                                    renderSystemsConfig.run { it.enabled }
                                }
                            }
                            is RenderMode.SingleFrame -> {
                                if (renderMode.frameRequested.get()) {
                                    renderSystems.filter {
                                        renderSystemsConfig.run { it.enabled }
                                    }
                                } else {
                                    renderSystems.filterIsInstance<ImGuiEditor>()
                                }.apply {
                                    renderMode.frameRequested.getAndSet(false)
                                }
                            }
                        }

                        profiled("Frame") {
                            val drawResult = currentReadState.latestDrawResult.apply { reset() }

                            profiled("renderSystems") {
                                renderSystems.groupBy { it.sharedRenderTarget }
                                    .forEach { (renderTarget, renderSystems) ->
                                        val clear = renderSystems.any { it.requiresClearSharedRenderTarget }
                                        renderTarget?.use(clear)
                                        renderSystems.forEach { renderSystem ->
                                            renderSystem.render(drawResult, currentReadState)
                                        }
                                    }

                                if (config.debug.isEditorOverlay) {
                                    renderSystems.forEach {
                                        it.renderEditor(drawResult, currentReadState)
                                    }
                                }
                            }

                            profiled("present") {
                                window.frontBuffer.use(true)
                                textureRenderer.drawToQuad(finalOutput.texture2D, mipMapLevel = finalOutput.mipmapLevel)
                                debugOutput.texture2D?.let { debugOutputTexture ->
                                    textureRenderer.drawToQuad(
                                        debugOutputTexture,
                                        buffer = debugBuffer,
                                        program = drawToDebugQuadProgram,
                                        mipMapLevel = debugOutput.mipmapLevel
                                    )
                                }
                            }

                            profiled("checkCommandSyncs") {
                                checkCommandSyncs()
                            }

                            val oldFenceSync = currentReadState.gpuCommandSync
                            profiled("finishFrame") {
                                finishFrame(currentReadState)
                                renderSystems.forEach {
                                    it.afterFrameFinished()
                                }
                            }
                            profiled("finish") {
                                GL11.glFinish()
                            }
                            profiled("swapBuffers") {
                                window.swapBuffers()
                            }
//                            require(oldFenceSync.isSignaled) {
//                                "GPU has not finished all actions using resources of read state, can't swap"
//                            }
                        }
                        GPUProfiler.dump()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    rendering.getAndSet(false)
                }
            })
        }
    }

    override fun processSystem() {
        while(config.debug.forceSingleThreadedRendering && rendering.get()) {
            Thread.onSpinWait()
        }
        renderSystems.distinct().forEach {
            it.update(world.delta)
        }
    }

}

class RenderSystemsConfig(renderSystems: List<RenderSystem>) {
    private val renderSystemsEnabled = renderSystems.distinct().associateWith { true }.toMutableMap()
    var RenderSystem.enabled: Boolean
        get() = renderSystemsEnabled[this] ?: true
        set(value) {
            renderSystemsEnabled[this] = value
        }
}

inline fun <T> profiled(name: String, action: () -> T): T {
    val task = GPUProfiler.start(name)
    try {
        return action()
    } finally {
        task?.end()
    }
}
