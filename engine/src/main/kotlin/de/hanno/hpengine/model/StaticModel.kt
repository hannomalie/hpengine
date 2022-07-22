package de.hanno.hpengine.model

import VertexStruktPackedImpl.Companion.sizeInBytes
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.scene.Vertex
import de.hanno.hpengine.scene.VertexStruktPacked
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.AABBData.Companion.getSurroundingAABB
import org.lwjgl.BufferUtils
import struktgen.TypedBuffer
import java.io.File

class StaticModel(
    override val file: File,
    meshes: List<StaticMesh>
) : Model<Vertex>(meshes) {

    override val path: String = file.absolutePath
    override val boundingVolume: AABB = calculateBoundingVolume()

    override fun calculateBoundingVolume() = AABB(meshes.map { it.spatial.boundingVolume.localAABB }.getSurroundingAABB())

    override val bytesPerVertex = VertexStruktPacked.sizeInBytes

    override val verticesPacked = TypedBuffer(BufferUtils.createByteBuffer(meshes.sumBy { it.vertices.size } * VertexStruktPacked.sizeInBytes), VertexStruktPacked.type).apply {
        byteBuffer.run {
            var counter = 0
            for(mesh in meshes) {
                for (vertex in mesh.vertices) {
                    this@apply[counter].run {
                        position.run { set(vertex.position) }
                        texCoord.run { set(vertex.texCoord) }
                        normal.run {set(vertex.normal) }
                    }
                    counter++
                }
            }
        }
    }

    override fun toString(): String = "StaticModel($path)"
}

