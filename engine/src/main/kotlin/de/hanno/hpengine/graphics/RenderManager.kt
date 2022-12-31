package de.hanno.hpengine.graphics

import com.artemis.BaseSystem
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.fps.FPSCounter
import de.hanno.hpengine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.graphics.vertexbuffer.QuadVertexBuffer
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.launchEndlessRenderLoop
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.stopwatch.GPUProfiler
import java.util.concurrent.atomic.AtomicBoolean

context(GraphicsApi)
class RenderManager(
    private val config: Config,
    private val input: Input,
    private val window: Window,
    programManager: ProgramManager,
    private val renderStateContext: RenderStateContext,
    private val finalOutput: FinalOutput,
    private val debugOutput: DebugOutput,
    private val fpsCounter: FPSCounter,
    private val renderSystemsConfig: RenderSystemsConfig,
    _renderSystems: List<RenderSystem>,
    private val gpuProfiler: GPUProfiler,
) : BaseSystem() {

    private val debugBuffer = QuadVertexBuffer(QuadVertexBuffer.quarterScreenVertices)

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
        this@GraphicsApi,
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
                gpuProfiler.run {
                    try {
                        renderStateContext.renderState.readLocked { currentReadState ->

                            val renderSystems = when (val renderMode = renderMode) {
                                RenderMode.Normal -> {
                                    renderSystems.filter {
                                        renderSystemsConfig.run { it.enabled }
                                    }
                                }
                                is RenderMode.SingleFrame -> {
                                    // TODO: Make rendersystems excludable from single step, like editor
                                    if (renderMode.frameRequested.get()) {
                                        renderSystems.filter {
                                            renderSystemsConfig.run { it.enabled }
                                        }
                                    } else {
                                        emptyList() // TODO: Check whether this still works
                                    }.apply {
                                        renderMode.frameRequested.getAndSet(false)
                                    }
                                }
                            }

                            profiled("Frame") {
                                profiled("renderSystems") {
                                    renderSystems.groupBy { it.sharedRenderTarget }
                                        .forEach { (renderTarget, renderSystems) ->
                                            val clear = renderSystems.any { it.requiresClearSharedRenderTarget }
                                            renderTarget?.use(clear)
                                            renderSystems.forEach { renderSystem ->
                                                renderSystem.render(currentReadState)
                                            }
                                        }

                                    if (config.debug.isEditorOverlay) {
                                        renderSystems.forEach {
                                            it.renderEditor(currentReadState)
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
                                    finish()
                                }
                                profiled("swapBuffers") {
                                    window.swapBuffers()
                                }
//                            require(oldFenceSync.isSignaled) {
//                                "GPU has not finished all actions using resources of read state, can't swap"
//                            }
                            }
                            gpuProfiler.dump()
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        rendering.getAndSet(false)
                    }
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
