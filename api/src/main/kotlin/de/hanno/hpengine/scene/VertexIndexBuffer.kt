package de.hanno.hpengine.scene

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.IndexBuffer
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import de.hanno.hpengine.graphics.buffer.typed
import org.lwjgl.BufferUtils
import struktgen.api.Strukt
import struktgen.api.StruktType

data class VertexIndexOffsets(override val vertexOffset: ElementCount, val indexOffset: ElementCount): GeometryOffset

class VertexIndexBuffer<T: Strukt>(
    graphicsApi: GraphicsApi,
    val type: StruktType<T>,
    indexBufferSizeInIntsCount: Int
): GeometryBuffer<T> {

    var indexBuffer: IndexBuffer = graphicsApi.IndexBuffer(
        graphicsApi,
        BufferUtils.createIntBuffer(indexBufferSizeInIntsCount)
    )
    var currentVertex = ElementCount(0)
        private set
    var currentIndex = ElementCount(0)
        private set

    // TODO: Remove synchronized with lock
    fun allocate(elementsCount: ElementCount, indicesCount: ElementCount): VertexIndexOffsets = synchronized(this) {
        VertexIndexOffsets(currentVertex, currentIndex).apply {
            currentVertex += elementsCount
            currentIndex += indicesCount
        }
    }

    fun resetAllocations() = synchronized(this) {
        currentVertex = ElementCount(0)
        currentIndex = ElementCount(0)
    }

    override var vertexStructArray: TypedGpuBuffer<T> = graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(type.sizeInBytes)).typed(type)
}
