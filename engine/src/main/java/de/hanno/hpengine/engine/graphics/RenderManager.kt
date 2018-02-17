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
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import de.hanno.hpengine.util.stopwatch.StopWatch
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

class RenderManager(val engine: Engine) : Manager {
    var recorder: RenderStateRecorder = SimpleRenderStateRecorder(engine)
    var renderThread: RenderThread = RenderThread(engine, "Render")

    val renderState: TripleBuffer<RenderState> = TripleBuffer(RenderState(engine.gpuContext), RenderState(engine.gpuContext), RenderState(engine.gpuContext), Consumer { renderState -> renderState.bufferEntities(engine.getScene().modelComponentSystem.components) })

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
                engine.renderer.startFrame()
                GPUProfiler.start("Prepare state")
                recorder.add(renderState.currentReadState)
                val latestDrawResult = renderState.currentReadState.latestDrawResult
                latestDrawResult.reset()
                GPUProfiler.end()
                engine.renderer.draw(latestDrawResult, renderState.currentReadState)
                latestDrawResult.GPUProfilingResult = GPUProfiler.dumpTimings()
                engine.renderer.endFrame()
                engine.sceneManager.scene.isInitiallyDrawn = true

                engine.eventBus.post(FrameFinishedEvent(latestDrawResult))
            }
            lastTimeSwapped = renderState.stopRead()
        }
    }

    fun resetAllocations() {
        engine.gpuContext.execute({
            StopWatch.getInstance().start("Scene init")
            engine.renderManager.vertexIndexBufferStatic.resetAllocations()
            engine.renderManager.vertexIndexBufferAnimated.resetAllocations()
            StopWatch.getInstance().stopAndPrintMS()
        }, true)
    }
    override fun clear() = resetAllocations()
}