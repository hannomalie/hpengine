package de.hanno.hpengine.scene

import AnimatedVertexStruktPackedImpl.Companion.type
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.pipelines.IndexBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.vertexbuffer.OpenGLIndexBuffer
import org.lwjgl.BufferUtils

context(GpuContext)
class VertexIndexBuffer(indexBufferSizeInIntsCount: Int) {

    var indexBuffer: IndexBuffer = OpenGLIndexBuffer(BufferUtils.createIntBuffer(indexBufferSizeInIntsCount))
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

    var vertexStructArray = PersistentMappedBuffer(VertexStruktPacked.type.sizeInBytes).typed(
        VertexStruktPacked.type
    )
    var animatedVertexStructArray = PersistentMappedBuffer(AnimatedVertexStruktPacked.type.sizeInBytes).typed(
        AnimatedVertexStruktPacked.type
    )

    data class VertexIndexOffsets(val vertexOffset: Int, val indexOffset: Int)
}