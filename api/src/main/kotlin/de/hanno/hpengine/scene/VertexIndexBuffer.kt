package de.hanno.hpengine.scene

import de.hanno.hpengine.graphics.buffer.IndexBuffer
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import struktgen.api.Strukt

interface IVertexIndexBuffer<SV: Strukt> {
    var indexBuffer: IndexBuffer
    var vertexStructArray: TypedGpuBuffer<SV>

    fun allocate(elementsCount: Int, indicesCount: Int): VertexIndexOffsets
    fun resetAllocations()
}
data class VertexIndexOffsets(val vertexOffset: Int, val indexOffset: Int)
