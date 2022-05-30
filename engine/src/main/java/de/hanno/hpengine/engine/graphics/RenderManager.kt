package de.hanno.hpengine.engine.graphics

import com.artemis.BaseSystem
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.imgui.ImGuiEditor
import de.hanno.hpengine.engine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.graphics.state.SimpleRenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.launchEndlessRenderLoop
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import net.miginfocom.swing.MigLayout
import org.lwjgl.opengl.GL11
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JPanel

data class FinalOutput(var texture2D: Texture2D, var mipmapLevel: Int = 0)
data class DebugOutput(var texture2D: Texture2D? = null, var mipmapLevel: Int = 0)

interface ConfigExtension {
    val panel: JPanel
}
class RenderSystemsConfig(val renderSystems: List<RenderSystem>) {
    private val renderSystemsEnabled = renderSystems.distinct().associateWith { true }.toMutableMap()
    var RenderSystem.enabled: Boolean
       get() = renderSystemsEnabled[this] ?: true
        set(value) {
            renderSystemsEnabled[this] = value
        }
}
class RenderSystemsConfigPanel(val renderSystemsConfig: RenderSystemsConfig): ConfigExtension {
    override val panel: JPanel = with(renderSystemsConfig) {
        JPanel().apply {
            border = BorderFactory.createTitledBorder("RenderSystems")
            layout = MigLayout("wrap 1")

            renderSystemsConfig.renderSystems.distinct().forEach { renderSystem ->
                add(JCheckBox(renderSystem::class.simpleName).apply {
                    isSelected = renderSystem.enabled
                    addActionListener {
                        renderSystem.enabled = !renderSystem.enabled
                    }
                })
            }
        }
    }
}

class RenderManager(
    val config: Config,
    val input: Input,
    val gpuContext: GpuContext<OpenGl>,
    val window: Window<OpenGl>,
    val programManager: ProgramManager<OpenGl>,
    val renderStateManager: RenderStateManager,
    val finalOutput: FinalOutput,
    val debugOutput: DebugOutput,
    val fpsCounter: FPSCounter,
    val renderSystemsConfig: RenderSystemsConfig,
    _renderSystems: List<RenderSystem>,
) : BaseSystem() {

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
//                val stateBeforeRendering = renderState.currentStateIndices
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
                                    mipMapLevel = debugOutput.mipmapLevel
                                )
                            }

                            profiled("finishFrame") {
                                gpuContext.finishFrame(currentReadState)
                                renderSystems.forEach {
                                    it.afterFrameFinished()
                                }
                            }

                            profiled("checkCommandSyncs") {
                                gpuContext.checkCommandSyncs()
                            }

                            window.swapBuffers()
                            GL11.glFinish()
                        }
                        GPUProfiler.dump()

//                        val stateAfterRendering = renderState.currentStateIndices
//                        if(stateBeforeRendering.first != stateAfterRendering.first) {
//                            println("Read state changed during rendering: $stateBeforeRendering -> $stateAfterRendering")
//                        }
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

inline fun <T> profiled(name: String, action: () -> T): T {
    val task = GPUProfiler.start(name)
    val result = action()
    task?.end()
    return result
}


private val OS = System.getProperty("os.name").toLowerCase()
private val isUnix = OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0