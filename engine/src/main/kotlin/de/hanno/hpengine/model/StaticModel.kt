package de.hanno.hpengine.model

import VertexStruktPackedImpl.Companion.sizeInBytes
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.scene.Vertex
import de.hanno.hpengine.scene.VertexStruktPacked
import de.hanno.hpengine.toCount
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.AABBData.Companion.getSurroundingAABB
import org.lwjgl.BufferUtils
import struktgen.api.TypedBuffer
import struktgen.api.forIndex
import java.io.File

class StaticModel(
    override val file: File,
    meshes: List<StaticMesh>
) : Model<Vertex>(meshes) {

    override val path: String = file.absolutePath
    override val boundingVolume: AABB = AABB(meshes.map { it.boundingVolume }.getSurroundingAABB())

    override val bytesPerVertex = VertexStruktPacked.sizeInBytes

    override val verticesPacked by lazy {
        TypedBuffer(
            BufferUtils.createByteBuffer(meshes.sumOf { it.vertices.size } * VertexStruktPacked.sizeInBytes),
            VertexStruktPacked.type
        ).apply {
            byteBuffer.run {
                indexedVertices.forEachIndexed { index, vertex ->
                    this@apply.forIndex(index) {
                        it.position.set(vertex.position)
                        it.texCoord.set(vertex.texCoord)
                        it.normal.set(vertex.normal)
                    }
                }
            }

        }
    }
    override val unindexedVerticesPacked by lazy {
        TypedBuffer(
            BufferUtils.createByteBuffer((triangleCount * 3 * SizeInBytes(VertexStruktPacked.sizeInBytes)).value.toInt()),
            VertexStruktPacked.type
        ).apply {
            byteBuffer.run {
                unindexedVertices.forEachIndexed { index, vertex ->
                    this@apply.forIndex(index) {
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
    }

    override fun toString(): String = "StaticModel($path)"
}

