package de.hanno.hpengine.graphics

import com.artemis.BaseSystem

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.imgui.editor.ImGuiEditor
import de.hanno.hpengine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateRecorder
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.graphics.state.SimpleRenderStateRecorder
import de.hanno.hpengine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.launchEndlessRenderLoop
import de.hanno.hpengine.model.texture.OpenGLTexture2D
import de.hanno.hpengine.graphics.fps.FPSCounter
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.stopwatch.GPUProfiler
import org.lwjgl.opengl.GL11
import java.util.concurrent.atomic.AtomicBoolean

data class FinalOutput(var texture2D: OpenGLTexture2D, var mipmapLevel: Int = 0)
data class DebugOutput(var texture2D: OpenGLTexture2D? = null, var mipmapLevel: Int = 0)

class RenderManager(
    val config: Config,
    val input: Input,
    val gpuContext: GpuContext,
    val window: Window,
    val programManager: ProgramManager,
    val renderStateManager: RenderStateManager,
    val finalOutput: FinalOutput,
    val debugOutput: DebugOutput,
    val fpsCounter: FPSCounter,
    val renderSystemsConfig: RenderSystemsConfig,
    _renderSystems: List<RenderSystem>,
) : BaseSystem() {

    private val drawToQuadProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/passthrough_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/simpletexture_fragment.glsl"))
    )
    var renderMode: RenderMode = RenderMode.Normal
    // TODO: Make this read only again
    var renderSystems: MutableList<RenderSystem> = _renderSystems.distinct().toMutableList()

    private val textureRenderer = SimpleTextureRenderer(
        config,
        gpuContext,
        finalOutput.texture2D,
        programManager,
        window.frontBuffer
    )

    inline val renderState: TripleBuffer<RenderState>
        get() = renderStateManager.renderState

    var recorder: RenderStateRecorder = SimpleRenderStateRecorder(input)

    fun finishCycle(deltaSeconds: Float) {
        renderState.currentWriteState.deltaSeconds = deltaSeconds
        renderState.swapStaging()
    }

    internal val rendering = AtomicBoolean(false)
    init {
        launchEndlessRenderLoop { deltaSeconds ->
            gpuContext.invoke(block = {
                rendering.getAndSet(true)
                try {
                    renderState.readLocked { currentReadState ->

                        val renderSystems = when(val renderMode = renderMode) {
                            RenderMode.Normal -> {
                                renderSystems.filter {
                                    renderSystemsConfig.run { it.enabled }
                                }
                            }
                            is RenderMode.SingleFrame -> {
                                if(renderMode.frameRequested.get()) {
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
                            recorder.add(currentReadState)
                            val drawResult = currentReadState.latestDrawResult.apply { reset() }

                            fun renderSystems() {
                                profiled("renderSystems") {
                                    renderSystems.groupBy { it.sharedRenderTarget }.forEach { (renderTarget, renderSystems) ->
                                        val clear = renderSystems.any { it.requiresClearSharedRenderTarget }
                                        renderTarget?.use(gpuContext, clear)
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
                            }

                            renderSystems()

                            window.frontBuffer.use(gpuContext, false)
                            textureRenderer.drawToQuad(finalOutput.texture2D, mipMapLevel = finalOutput.mipmapLevel)
                            debugOutput.texture2D?.let { debugOutputTexture ->
                                textureRenderer.drawToQuad(
                                    debugOutputTexture,
                                    buffer = gpuContext.debugBuffer,
                                    program = drawToQuadProgram,
                                    mipMapLevel = debugOutput.mipmapLevel
                                )
                            }

                            profiled("checkCommandSyncs") {
                                gpuContext.checkCommandSyncs()
                            }

                            val oldFenceSync = currentReadState.gpuCommandSync
                            profiled("finishFrame") {
                                gpuContext.finishFrame(currentReadState)
                                renderSystems.forEach {
                                    it.afterFrameFinished()
                                }
                            }

                            window.swapBuffers()
                            GL11.glFinish()
                            require(oldFenceSync.isSignaled) {
                                "GPU has not finished all actions using resources of read state, can't swap"
                            }
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
    val result = action()
    task?.end()
    return result
}
