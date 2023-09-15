package de.hanno.hpengine.graphics.renderer.pipelines

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.AtomicCounterBuffer
import de.hanno.hpengine.graphics.renderer.RenderBatch

class CommandOrganization(graphicsApi: GraphicsApi) {
    var commandCount = 0
    var filteredRenderBatches: List<RenderBatch> = emptyList()
    val commandBuffer = CommandBuffer(graphicsApi, 10000)

    val entityOffsetBuffer = IndexBuffer(graphicsApi, 10000)
    val drawCountBuffer = graphicsApi.AtomicCounterBuffer()
}
