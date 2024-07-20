package de.hanno.hpengine.scene

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import de.hanno.hpengine.graphics.buffer.typed
import struktgen.api.Strukt
import struktgen.api.StruktType

data class VertexOffsets(override val vertexOffset: ElementCount): GeometryOffset

class VertexBuffer<T: Strukt>(
    graphicsApi: GraphicsApi,
    val type: StruktType<T>
): GeometryBuffer<T> {

    var currentVertex = ElementCount(0)
        private set

    // TODO: Remove synchronized with lock
    fun allocate(elementsCount: ElementCount) = synchronized(this) {
        VertexOffsets(currentVertex).apply {
            currentVertex += elementsCount
        }
    }

    fun resetAllocations() = synchronized(this) {
        currentVertex = ElementCount(0)
    }

    override var vertexStructArray: TypedGpuBuffer<T> = graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(type.sizeInBytes)).typed(type)
}
