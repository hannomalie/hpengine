package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.Vertex
import de.hanno.hpengine.engine.scene.VertexStruct
import de.hanno.hpengine.engine.scene.VertexStructPacked
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.AABBData.Companion.getSurroundingAABB
import de.hanno.struct.StructArray
import java.io.File

class StaticModel(override val file: File,
                  meshes: List<StaticMesh>,
                  material: Material = meshes.first().material) : AbstractModel<Vertex>(meshes, material) {

    override val path: String = file.absolutePath
    override val boundingVolume: AABB = calculateBoundingVolume()

    override fun calculateBoundingVolume() = AABB(meshes.map { it.spatial.boundingVolume.localAABB }.getSurroundingAABB())

    override val bytesPerVertex = VertexStruct.sizeInBytes

    override val verticesStructArray = StructArray(uniqueVertices.size) { VertexStruct() }.apply {
        for (i in uniqueVertices.indices) {
            val vertex = uniqueVertices[i]
            val (position, texCoord, normal) = vertex
            val target = getAtIndex(i)
            target.position.set(position)
            target.texCoord.set(texCoord)
            target.normal.set(normal)
        }
    }

    override val verticesStructArrayPacked = StructArray(meshes.sumBy { it.vertices.size }) { VertexStructPacked() }.apply {
        var counter = 0
        for(mesh in meshes) {
            for(vertex in mesh.vertices) {
                val (position, texCoord, normal) = vertex
                val target = getAtIndex(counter)
                target.position.set(position)
                target.texCoord.set(texCoord)
                target.normal.set(normal)
                counter++
            }
        }
    }

    override fun toString(): String = "StaticModel($path)"
}
