package de.hanno.hpengine.scene

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.IndexBuffer
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import de.hanno.hpengine.graphics.buffer.typed
import org.lwjgl.BufferUtils
import struktgen.api.Strukt
import struktgen.api.StruktType

interface IVertexIndexBuffer<SV: Strukt> {
    var indexBuffer: IndexBuffer
    var vertexStructArray: TypedGpuBuffer<SV>

    fun allocate(elementsCount: Int, indicesCount: Int): VertexIndexOffsets
    fun resetAllocations()
}
data class VertexIndexOffsets(val vertexOffset: Int, val indexOffset: Int)

class VertexIndexBuffer<T: Strukt>(
    graphicsApi: GraphicsApi,
    val type: StruktType<T>, indexBufferSizeInIntsCount: Int
): IVertexIndexBuffer<T> {

    override var indexBuffer: IndexBuffer = graphicsApi.IndexBuffer(
        graphicsApi,
        BufferUtils.createIntBuffer(indexBufferSizeInIntsCount)
    )
    // TODO: It's invalid to use a single index for two vertex arrays, move animated vertex array out of here
    private var currentBaseVertex = 0
    private var currentIndexOffset = 0

    // TODO: Remove synchronized with lock
    override fun allocate(elementsCount: Int, indicesCount: Int): VertexIndexOffsets = synchronized(this) {
        VertexIndexOffsets(currentBaseVertex, currentIndexOffset).apply {
            currentBaseVertex += elementsCount
            currentIndexOffset += indicesCount
        }
    }

    override fun resetAllocations() {
        currentBaseVertex = 0
        currentIndexOffset = 0
    }

    override var vertexStructArray: TypedGpuBuffer<T> = graphicsApi.PersistentShaderStorageBuffer(type.sizeInBytes).typed(type)
}
