package de.hanno.hpengine.scene

import AnimatedVertexStruktPackedImpl.Companion.type
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.vertexbuffer.IndexBuffer
import org.lwjgl.BufferUtils

class VertexIndexBuffer(gpuContext: GpuContext<*>,
                        indexBufferSizeInIntsCount: Int) {

    var indexBuffer = IndexBuffer(gpuContext, BufferUtils.createIntBuffer(indexBufferSizeInIntsCount))
    private var currentBaseVertex = 0
    private var currentIndexOffset = 0

    fun allocate(elementsCount: Int, indicesCount: Int): VertexIndexOffsets = synchronized(this) {
        VertexIndexOffsets(currentBaseVertex, currentIndexOffset).apply {
            currentBaseVertex += elementsCount
            currentIndexOffset += indicesCount
        }
    }

    fun resetAllocations() {
        currentBaseVertex = 0
        currentIndexOffset = 0
    }

    var vertexStructArray = PersistentMappedBuffer(VertexStruktPacked.type.sizeInBytes, gpuContext).typed(
        VertexStruktPacked.type)
    var animatedVertexStructArray = PersistentMappedBuffer(AnimatedVertexStruktPacked.type.sizeInBytes, gpuContext).typed(
        AnimatedVertexStruktPacked.type)

    data class VertexIndexOffsets(val vertexOffset: Int, val indexOffset: Int)
}