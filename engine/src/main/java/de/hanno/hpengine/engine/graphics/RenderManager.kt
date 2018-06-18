package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.event.FrameFinishedEvent
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.SimpleRenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.scene.AnimatedVertex
import de.hanno.hpengine.engine.scene.Vertex
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.threads.RenderThread
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import de.hanno.hpengine.util.stopwatch.StopWatch
import java.util.concurrent.atomic.AtomicLong

class RenderManager<T: RenderState>(val engine: Engine, gpuContext: GpuContext, renderStateFactory: () -> T) : Manager {
    private var lastFrameTime = 0L
    private val fpsCounter = FPSCounter()

    var recorder: RenderStateRecorder = SimpleRenderStateRecorder(engine)
    var renderThread: RenderThread = RenderThread(engine, "Render")

    val renderState: TripleBuffer<T> = TripleBuffer(renderStateFactory(),
                                                              renderStateFactory(),
                                                              renderStateFactory())

    val vertexIndexBufferStatic = VertexIndexBuffer<Vertex>(engine.gpuContext, 10, 10, ModelComponent.DEFAULTCHANNELS)
    val vertexIndexBufferAnimated = VertexIndexBuffer<AnimatedVertex>(engine.gpuContext, 10, 10, ModelComponent.DEFAULTANIMATEDCHANNELS)

    val drawCycle = AtomicLong()
    var cpuGpuSyncTimeNs: Long = 0
        set(cpuGpuSyncTimeNs) {
            field = (this.cpuGpuSyncTimeNs + cpuGpuSyncTimeNs) / 2
        }

    val drawRunnable: Runnable = object : Runnable {
        internal var lastTimeSwapped = true
        override fun run() {
            renderState.startRead()

            if (lastTimeSwapped) {
                engine.input.update()
                val drawResult = profilingFramed {
                    recorder.add(renderState.currentReadState)
                    val drawResult = renderState.currentReadState.latestDrawResult.apply { reset() }

                    engine.getScene().renderStateConsumers.forEach {
                        it.render(drawResult, renderState.currentReadState)
                    }
                    engine.renderer.render(drawResult, renderState.currentReadState)
                    drawResult.apply { GPUProfilingResult = GPUProfiler.dumpTimings() }
                }
                lastFrameTime = System.currentTimeMillis()
                fpsCounter.update()
                engine.sceneManager.scene.isInitiallyDrawn = true

                engine.eventBus.post(FrameFinishedEvent(drawResult))
            }
            lastTimeSwapped = renderState.stopRead()
        }
    }
    val perFrameCommand = SimpleProvider(drawRunnable).also { gpuContext.registerPerFrameCommand(it) }

    fun getDeltaInMS() = System.currentTimeMillis().toDouble() - lastFrameTime.toDouble()

    fun getDeltaInS() = getDeltaInMS() / 1000.0

    fun getCurrentFPS() = fpsCounter.fps

    fun getMsPerFrame() = fpsCounter.msPerFrame

    fun resetAllocations() {
        engine.gpuContext.execute({
            StopWatch.getInstance().start("Scene init")
            engine.renderManager.vertexIndexBufferStatic.resetAllocations()
            engine.renderManager.vertexIndexBufferAnimated.resetAllocations()
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