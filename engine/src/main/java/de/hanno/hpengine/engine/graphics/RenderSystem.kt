package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.SimpleRenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.threads.RenderThread
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

class RenderSystem {

    var recorder: RenderStateRecorder = SimpleRenderStateRecorder()
    var renderThread: RenderThread = RenderThread("Render")

    val renderState: TripleBuffer<RenderState> = TripleBuffer(RenderState(), RenderState(), RenderState(), Consumer { renderState -> renderState.bufferEntities(Engine.getInstance().scene.entities) })

    val drawCycle = AtomicLong()
    var cpuGpuSyncTimeNs: Long = 0
        set(cpuGpuSyncTimeNs) {
            field = (this.cpuGpuSyncTimeNs + cpuGpuSyncTimeNs) / 2
        }
}