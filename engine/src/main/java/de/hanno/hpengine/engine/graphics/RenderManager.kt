package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.LineRenderer
import de.hanno.hpengine.engine.graphics.renderer.LineRendererImpl
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.SimpleRenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import java.util.concurrent.atomic.AtomicLong

class RenderStateManager(renderStateFactory: () -> RenderState) {
    val renderState: TripleBuffer<RenderState> = TripleBuffer(renderStateFactory,
            { currentStaging, currentRead -> currentStaging.cycle < currentRead.cycle })
}
class RenderManager(val engineContext: EngineContext<OpenGl>, // TODO: Make generic
                    val renderStateManager: RenderStateManager = engineContext.renderStateManager,
                    val lineRenderer: LineRenderer = LineRendererImpl(engineContext),
                    val materialManager: MaterialManager = engineContext.materialManager) : Manager {

    inline val renderState: TripleBuffer<RenderState>
        get() = renderStateManager.renderState
    private var lastFrameTime = 0L
    val fpsCounter = FPSCounter()

    var recorder: RenderStateRecorder = SimpleRenderStateRecorder(engineContext.input)

    val drawCycle = AtomicLong()
    var cpuGpuSyncTimeNs: Long = 0
        set(cpuGpuSyncTimeNs) {
            field = (this.cpuGpuSyncTimeNs + cpuGpuSyncTimeNs) / 2
        }

    init {
        var lastTimeSwapped = true
        val runnable = object: Runnable {
            override fun run() {
                try {
                    renderState.startRead()

                    if (lastTimeSwapped) {
                        recorder.add(renderState.currentReadState)
                        val drawResult = renderState.currentReadState.latestDrawResult.apply { reset() }

                        profiled("renderSystems") {
                            engineContext.renderSystems.forEach {
                                it.render(drawResult, renderState.currentReadState)
                            }
                        }

                        profiled("finishFrame") {
                            engineContext.gpuContext.finishFrame(renderState.currentReadState)
                            engineContext.renderSystems.forEach {
//                                profiled(it.javaClass.simpleName) {
                                    it.afterFrameFinished()
//                                }
                            }
                        }

                        profiled("checkCommandSyncs") {
                            engineContext.gpuContext.checkCommandSyncs()
                        }

                        GPUProfiler.dump()

                        lastFrameTime = System.currentTimeMillis()
                        fpsCounter.update()

                    }
                    lastTimeSwapped = renderState.stopRead()

                    engineContext.window.title = "HPEngine - ${fpsCounter.fps.toInt()} fps - ${fpsCounter.msPerFrame} ms"
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                engineContext.gpuContext.execute("Foo", this, false, true)
            }

        }
        engineContext.gpuContext.execute("Foo", runnable, false, true)
    }

    fun getDeltaInMS() = System.currentTimeMillis().toDouble() - lastFrameTime.toDouble()

    fun getDeltaInS() = getDeltaInMS() / 1000.0

    fun getCurrentFPS() = fpsCounter.fps

    fun getMsPerFrame() = fpsCounter.msPerFrame

}

inline fun <T> profiled(name: String, action: () -> T): T {
    val task = GPUProfiler.start(name)
    val result = action()
    task?.end()
    return result
}