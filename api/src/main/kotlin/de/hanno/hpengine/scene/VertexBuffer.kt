package de.hanno.hpengine.scene

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import de.hanno.hpengine.graphics.buffer.typed
import struktgen.api.Strukt
import struktgen.api.StruktType

data class VertexOffsets(val vertexOffset: Int)

class VertexBuffer<T: Strukt>(
    graphicsApi: GraphicsApi,
    val type: StruktType<T>
) {

    // TODO: It's invalid to use a single index for two vertex arrays, move animated vertex array out of here
    private var currentBaseVertex = 0

    // TODO: Remove synchronized with lock
    fun allocate(elementsCount: Int) = synchronized(this) {
        VertexOffsets(currentBaseVertex).apply {
            currentBaseVertex += elementsCount
        }
    }

    fun resetAllocations() {
        currentBaseVertex = 0
    }

    var vertexStructArray: TypedGpuBuffer<T> = graphicsApi.PersistentShaderStorageBuffer(type.sizeInBytes).typed(type)
}
