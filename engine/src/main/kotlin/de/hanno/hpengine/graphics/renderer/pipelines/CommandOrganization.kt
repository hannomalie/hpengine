package de.hanno.hpengine.graphics.renderer.pipelines

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.AtomicCounterBuffer
import de.hanno.hpengine.graphics.renderer.RenderBatch

context(GpuContext)
class CommandOrganization {
    var commandCount = 0
    var filteredRenderBatches: List<RenderBatch> = emptyList()
    val commandBuffer = CommandBuffer(10000)

    val entityOffsetBuffer = IndexBuffer(10000)
    val drawCountBuffer = AtomicCounterBuffer(1)
}
