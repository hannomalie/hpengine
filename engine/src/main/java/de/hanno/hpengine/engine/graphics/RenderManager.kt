package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.graphics.state.SimpleRenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.launchEndlessRenderLoop
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import imgui.ImGui
import kotlinx.coroutines.delay
import net.miginfocom.swing.MigLayout
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JPanel

data class FinalOutput(val texture2D: Texture2D)

interface ConfigExtension {
    val panel: JPanel
}
class RenderSystemsConfig(val renderSystems: List<RenderSystem>) {
    private val renderSystemsEnabled = renderSystems.distinct().associateWith { true }.toMutableMap()
    var RenderSystem.enabled: Boolean
       get() = renderSystemsEnabled[this]!!
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
    val fpsCounter: FPSCounter,
    val renderSystemsConfig: RenderSystemsConfig,
    _renderSystems: List<RenderSystem>,
) : Manager {

    val renderSystems: List<RenderSystem> = _renderSystems.distinct()

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

    fun finishCycle(scene: Scene, deltaSeconds: Float) {
        renderState.currentWriteState.deltaSeconds = deltaSeconds
        scene.extract(renderState.currentWriteState)
        extract(scene, renderState.currentWriteState)
        renderState.swapStaging()
    }

    override fun beforeSetScene(nextScene: Scene) { renderSystems.forEach { it.beforeSetScene(nextScene) } }

    override fun afterSetScene(lastScene: Scene?, currentScene: Scene) { renderSystems.forEach { it.afterSetScene(currentScene) } }

    init {
        launchEndlessRenderLoop { deltaSeconds ->
            gpuContext.invoke(block = {
                try {
                    val currentReadState = renderState.startRead()

                    val renderSystems = renderSystems.filter {
                        renderSystemsConfig.run { it.enabled }
                    }

                    profiled("Frame") {
                        recorder.add(currentReadState)
                        val drawResult = currentReadState.latestDrawResult.apply { reset() }

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

                        window.frontBuffer.use(gpuContext, false)
                        textureRenderer.drawToQuad(finalOutput.texture2D)

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
                    }
                    GPUProfiler.dump()

                    renderState.stopRead()

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            })
            // https://bugs.openjdk.java.net/browse/JDK-4852178
            // TODO: Remove this delay if possible anyhow, this is just so that the editor is not that unresponsive because of canvas locking
//            if(isUnix) {
                delay(5)
//            }
        }
    }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        renderSystems.distinct().forEach {
            it.run { update(scene, deltaSeconds) }
        }
    }

    fun getCurrentFPS() = fpsCounter.fps

    fun getMsPerFrame() = fpsCounter.msPerFrame

}

inline fun <T> profiled(name: String, action: () -> T): T {
    val task = GPUProfiler.start(name)
    val result = action()
    task?.end()
    return result
}


private val OS = System.getProperty("os.name").toLowerCase()
private val isUnix = OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0