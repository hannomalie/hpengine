package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.PerFrameCommandProvider
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.event.FrameFinishedEvent
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.SimpleRenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.scene.AnimatedVertex
import de.hanno.hpengine.engine.scene.Vertex
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.engine.threads.RenderThread
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

class RenderSystem {

    var recorder: RenderStateRecorder = SimpleRenderStateRecorder()
    var renderThread: RenderThread = RenderThread("Render")

    val renderState: TripleBuffer<RenderState> = TripleBuffer(RenderState(), RenderState(), RenderState(), Consumer { renderState -> renderState.bufferEntities(Engine.getInstance().sceneManager.scene.entities) })

    val vertexIndexBufferStatic = VertexIndexBuffer<Vertex>(10, 10, ModelComponent.DEFAULTCHANNELS)
    val vertexIndexBufferAnimated = VertexIndexBuffer<AnimatedVertex>(10, 10, ModelComponent.DEFAULTANIMATEDCHANNELS)

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
                Input.update()
                Renderer.getInstance().startFrame()
                GPUProfiler.start("Prepare state")
                recorder!!.add(renderState.currentReadState)
                val latestDrawResult = renderState.currentReadState.latestDrawResult
                latestDrawResult.reset()
                GPUProfiler.end()
                Renderer.getInstance().draw(latestDrawResult, renderState.currentReadState)
                latestDrawResult.GPUProfilingResult = GPUProfiler.dumpTimings()
                Renderer.getInstance().endFrame()
                Engine.getInstance().sceneManager.scene.isInitiallyDrawn = true

                Engine.eventBus.post(FrameFinishedEvent(latestDrawResult))
            }
            lastTimeSwapped = renderState.stopRead()
        }
    }
}