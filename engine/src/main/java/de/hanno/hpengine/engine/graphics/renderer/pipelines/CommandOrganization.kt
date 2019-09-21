package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer
import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer
import org.lwjgl.BufferUtils.createIntBuffer
import java.util.ArrayList

class CommandOrganization(gpuContext: GpuContext<*>) {
    var commandCount = 0
    var primitiveCount = 0
    val commandBuffer = CommandBuffer(gpuContext, 10000)

    val offsets = IndexBuffer(gpuContext, 10000)
    val entityOffsetBuffer = IndexBuffer(gpuContext, 10000)
    val drawCountBuffer = AtomicCounterBuffer(gpuContext, 1)

    val drawCountBuffers = AtomicCounterBuffer(gpuContext, 1)
    val visibilityBuffers = IndexBuffer(gpuContext, 10000)
    val entityOffsetBuffers = IndexBuffer(gpuContext, 10000)
    val commandOffsets = IndexBuffer(gpuContext, 10000)
    val currentCompactedPointers = IndexBuffer(gpuContext, 10000)
    val entityOffsetBuffersCulled = IndexBuffer(gpuContext, 10000)
    val entitiesBuffersCompacted = PersistentMappedBuffer(gpuContext, 8000)
    val entitiesCompactedCounter = AtomicCounterBuffer(gpuContext, 1)
    val entitiesCounters = IndexBuffer(gpuContext)
}