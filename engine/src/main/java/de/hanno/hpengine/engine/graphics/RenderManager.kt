package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.event.FrameFinishedEvent
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.SimpleRenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.threads.RenderThread
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import de.hanno.hpengine.util.stopwatch.StopWatch
import java.util.concurrent.atomic.AtomicLong

class RenderStateManager(renderStateFactory: () -> RenderState) {
    val renderState: TripleBuffer<RenderState> = TripleBuffer(renderStateFactory(),
            renderStateFactory(),
            renderStateFactory())
}
class RenderManager(val engineContext: EngineContext,
                    val renderStateManager: RenderStateManager,
                    val renderer: Renderer) : Manager {

    inline val renderState: TripleBuffer<RenderState>
        get() = renderStateManager.renderState
    private var lastFrameTime = 0L
    val fpsCounter = FPSCounter()

    var recorder: RenderStateRecorder = SimpleRenderStateRecorder(engineContext)
    var renderThread: RenderThread = RenderThread("Render") {
        perFrameCommand.setReadyForExecution()
    }

    val vertexIndexBufferStatic = VertexIndexBuffer(engineContext.gpuContext, 10, 10, ModelComponent.DEFAULTCHANNELS)
    val vertexIndexBufferAnimated = VertexIndexBuffer(engineContext.gpuContext, 10, 10, ModelComponent.DEFAULTANIMATEDCHANNELS)

    val drawCycle = AtomicLong()
    var cpuGpuSyncTimeNs: Long = 0
        set(cpuGpuSyncTimeNs) {
            field = (this.cpuGpuSyncTimeNs + cpuGpuSyncTimeNs) / 2
        }

    val drawRunnable: Runnable = object : Runnable {
        var lastTimeSwapped = true
        override fun run() {
            renderState.startRead()

            if (lastTimeSwapped) {
                engineContext.input.update()
                val drawResult = profilingFramed {
                    recorder.add(renderState.currentReadState)
                    val drawResult = renderState.currentReadState.latestDrawResult.apply { reset() }

                    engineContext.renderSystems.forEach {
                        it.render(drawResult, renderState.currentReadState)
                    }
                    renderer.render(drawResult, renderState.currentReadState)
                    drawResult.apply { GPUProfilingResult = GPUProfiler.dumpTimings() }
                }
                lastFrameTime = System.currentTimeMillis()
                fpsCounter.update()

                engineContext.eventBus.post(FrameFinishedEvent(drawResult))
            }
            lastTimeSwapped = renderState.stopRead()
        }
    }
    val perFrameCommand = SimpleProvider(drawRunnable).also { engineContext.gpuContext.registerPerFrameCommand(it) }

    fun getDeltaInMS() = System.currentTimeMillis().toDouble() - lastFrameTime.toDouble()

    fun getDeltaInS() = getDeltaInMS() / 1000.0

    fun getCurrentFPS() = fpsCounter.fps

    fun getMsPerFrame() = fpsCounter.msPerFrame

    fun resetAllocations() {
        engineContext.gpuContext.execute({
            StopWatch.getInstance().start("SimpleScene init")
            vertexIndexBufferStatic.resetAllocations()
            vertexIndexBufferAnimated.resetAllocations()
            StopWatch.getInstance().stopAndPrintMS()
        }, true)
    }
    override fun clear() = resetAllocations()

    inline fun <T> profiled(name: String, action: () -> T): T {
        GPUProfiler.start(name)
        val result = action()
        GPUProfiler.end()
        return result
    }
    inline fun <T> profilingFramed(action: () -> T): T {
        GPUProfiler.startFrame()
        val result: T = action()
        GPUProfiler.endFrame()
        return result
    }
}