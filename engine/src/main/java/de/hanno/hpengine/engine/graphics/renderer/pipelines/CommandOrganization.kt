package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch

class CommandOrganization(gpuContext: GpuContext<*>) {
    var commandCount = 0
    var filteredRenderBatches: List<RenderBatch> = emptyList()
    val commandBuffer = CommandBuffer(gpuContext, 10000)

    val entityOffsetBuffer = IndexBuffer(gpuContext, 10000)
    val drawCountBuffer = AtomicCounterBuffer(gpuContext, 1)
}
