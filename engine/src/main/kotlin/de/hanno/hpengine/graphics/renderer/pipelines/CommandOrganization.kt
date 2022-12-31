package de.hanno.hpengine.graphics.renderer.pipelines

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.AtomicCounterBuffer
import de.hanno.hpengine.graphics.renderer.RenderBatch

context(GraphicsApi)
class CommandOrganization {
    var commandCount = 0
    var filteredRenderBatches: List<RenderBatch> = emptyList()
    val commandBuffer = CommandBuffer(10000)

    val entityOffsetBuffer = OpenGLIndexBuffer(10000)
    val drawCountBuffer = AtomicCounterBuffer()
}
