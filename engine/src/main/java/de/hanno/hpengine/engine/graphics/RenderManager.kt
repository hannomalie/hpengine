package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.graphics.renderer.LineRenderer
import de.hanno.hpengine.engine.graphics.renderer.LineRendererImpl
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.SimpleRenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import de.hanno.hpengine.util.stopwatch.StopWatch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

class RenderStateManager(renderStateFactory: () -> RenderState) {
    val renderState: TripleBuffer<RenderState> = TripleBuffer(renderStateFactory(),
            renderStateFactory(),
            renderStateFactory())
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

    val vertexIndexBufferStatic = VertexIndexBuffer(engineContext.gpuContext, 10, 10, ModelComponent.DEFAULTCHANNELS)
    val vertexIndexBufferAnimated = VertexIndexBuffer(engineContext.gpuContext, 10, 10, ModelComponent.DEFAULTANIMATEDCHANNELS)

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

                        engineContext.renderSystems.forEach {
                            it.render(drawResult, renderState.currentReadState)
                        }
                        engineContext.gpuContext.finishFrame(renderState.currentReadState)
                        engineContext.renderSystems.forEach {
                            it.afterFrameFinished()
                        }
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

    fun resetAllocations() {
        engineContext.gpuContext.execute("resetAllocations", Runnable {
            StopWatch.getInstance().start("SimpleScene init")
            vertexIndexBufferStatic.resetAllocations()
            vertexIndexBufferAnimated.resetAllocations()
            StopWatch.getInstance().stopAndPrintMS()
        })
    }
    override fun clear() = resetAllocations()

    override fun extract(renderState: RenderState) {
        renderState.entitiesState.vertexIndexBufferStatic = vertexIndexBufferStatic
        renderState.entitiesState.vertexIndexBufferAnimated = vertexIndexBufferAnimated
    }
}

inline fun <T> profiled(name: String, action: () -> T): T {
    val task = GPUProfiler.start(name)
    val result = action()
    task?.end()
    return result
}