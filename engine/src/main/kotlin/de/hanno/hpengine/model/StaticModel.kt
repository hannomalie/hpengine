package de.hanno.hpengine.model

import VertexStruktPackedImpl.Companion.sizeInBytes
import de.hanno.hpengine.scene.Vertex
import de.hanno.hpengine.scene.VertexStruktPacked
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.AABBData.Companion.getSurroundingAABB
import struktgen.api.TypedBuffer
import struktgen.api.forIndex
import java.io.File


fun StaticModel.putVerticesPacked(buffer: TypedBuffer<VertexStruktPacked>, baseIndex: Int) = buffer.run {
    byteBuffer.run {
        indexedVertices.forEachIndexed { index, vertex ->
            buffer.forIndex(baseIndex + index) {
                it.position.set(vertex.position)
                it.texCoord.set(vertex.texCoord)
                it.normal.set(vertex.normal)
            }
        }
    }
}
fun StaticModel.putUnindexedVerticesPacked(buffer: TypedBuffer<VertexStruktPacked>, baseIndex: Int) = buffer.run {
    byteBuffer.run {
        unindexedVertices.forEachIndexed { index, vertex ->
            buffer.forIndex(baseIndex + index) {
                it.position.set(vertex.position)
                it.texCoord.set(vertex.texCoord)
                it.normal.set(vertex.normal)
                when (index % 3) {
                    0 -> it.dummy.x = 1f
                    1 -> it.dummy.y = 1f
                    2 -> it.dummy.z = 1f
                }
            }
        }
    }
}

class StaticModel(
    override val file: File,
    meshes: List<StaticMesh>
) : Model<Vertex>(meshes) {

    override val path: String = file.absolutePath
    override val boundingVolume: AABB = AABB(meshes.map { it.boundingVolume }.getSurroundingAABB())

    override val bytesPerVertex = VertexStruktPacked.sizeInBytes
    override fun toString(): String = "StaticModel($path)"
}

