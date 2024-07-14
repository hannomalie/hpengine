package de.hanno.hpengine.graphics.renderer.pipelines

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.AtomicCounterBuffer
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.toCount

class CommandOrganization(graphicsApi: GraphicsApi) {
    var commandCount = 0
    var filteredRenderBatches: List<RenderBatch> = emptyList()
    val commandBuffer = CommandBuffer(graphicsApi, 10000.toCount())

    val entityOffsetBuffer = IndexBuffer(graphicsApi, 10000.toCount())
    val drawCountBuffer = graphicsApi.AtomicCounterBuffer()
}
