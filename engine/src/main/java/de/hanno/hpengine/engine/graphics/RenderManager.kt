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
import kotlinx.coroutines.delay

data class FinalOutput(val texture2D: Texture2D)

class RenderManager(
    val config: Config,
    val input: Input,
    val gpuContext: GpuContext<OpenGl>,
    val window: Window<OpenGl>,
    val programManager: ProgramManager<OpenGl>,
    val renderStateManager: RenderStateManager,
    val finalOutput: FinalOutput,
    val renderSystems: List<RenderSystem>
) : Manager {

    private val textureRenderer = SimpleTextureRenderer(
        config,
        gpuContext,
        finalOutput.texture2D,
        programManager,
        window.frontBuffer
    )

    inline val renderState: TripleBuffer<RenderState>
        get() = renderStateManager.renderState
    val fpsCounter = FPSCounter()

    var recorder: RenderStateRecorder = SimpleRenderStateRecorder(input)

    fun finishCycle(scene: Scene, deltaSeconds: Float) {
        renderState.currentWriteState.deltaSeconds = deltaSeconds
        scene.extract(renderState.currentWriteState)
        extract(scene, renderState.currentWriteState)
        renderState.swapStaging()
    }
    init {
        var lastTimeSwapped = true
        launchEndlessRenderLoop { deltaSeconds ->
            gpuContext.invoke(block = {
                try {
                    renderState.startRead()

                    if (lastTimeSwapped) {
                        profiled("Frame") {
                            recorder.add(renderState.currentReadState)
                            val drawResult = renderState.currentReadState.latestDrawResult.apply { reset() }

                            profiled("renderSystems") {
                                renderSystems.groupBy { it.sharedRenderTarget }.forEach { (renderTarget, renderSystems) ->

                                    val clear = renderSystems.any { it.requiresClearSharedRenderTarget }
                                    gpuContext.clearColor(1f,0f,0f,1f)
                                    renderTarget?.use(gpuContext, clear)
                                    renderSystems.forEachIndexed { index, renderSystem ->
                                        renderSystem.render(drawResult, renderState.currentReadState)
                                    }
                                }

                                if (config.debug.isEditorOverlay) {
                                    renderSystems.forEach {
                                        it.renderEditor(drawResult, renderState.currentReadState)
                                    }
                                }
                            }

                            window.frontBuffer.use(gpuContext, false)
                            textureRenderer.drawToQuad(finalOutput.texture2D)

                            profiled("finishFrame") {
                                gpuContext.finishFrame(renderState.currentReadState)
                                renderSystems.forEach {
                                    it.afterFrameFinished()
                                }
                            }

                            profiled("checkCommandSyncs") {
                                gpuContext.checkCommandSyncs()
                            }

                            window.swapBuffers()
                            fpsCounter.update()

                        }
                        GPUProfiler.dump()

                    }
                    lastTimeSwapped = renderState.stopRead()

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
//        GlobalScope.launch {
//            while(true) {
//                gpuContext.invoke(block = {
//                    runnable()
//                })
//                // https://bugs.openjdk.java.net/browse/JDK-4852178
//                // TODO: Remove this delay if possible anyhow, this is just so that the editor is not that unresponsive because of canvas locking
//                if(isUnix) {
//                    delay(5)
//                }
//            }
//        }
    }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {

        this@RenderManager.renderSystems.forEach {
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