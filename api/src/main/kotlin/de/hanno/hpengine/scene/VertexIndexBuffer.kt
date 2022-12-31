package de.hanno.hpengine.scene

import de.hanno.hpengine.graphics.buffer.IndexBuffer
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import struktgen.api.Strukt

interface IVertexIndexBuffer<SV: Strukt, AV: Strukt> {
    var indexBuffer: IndexBuffer
    fun allocate(elementsCount: Int, indicesCount: Int): VertexIndexOffsets
    fun resetAllocations()
    var vertexStructArray: TypedGpuBuffer<SV>
    var animatedVertexStructArray: TypedGpuBuffer<AV>
}
data class VertexIndexOffsets(val vertexOffset: Int, val indexOffset: Int)
