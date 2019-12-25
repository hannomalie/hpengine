package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.scene.Vertex
import de.hanno.hpengine.engine.scene.VertexStruct
import de.hanno.hpengine.engine.scene.VertexStructPacked
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.struct.StructArray

class StaticModel(private val path: String,
                  meshes: List<StaticMesh>) : AbstractModel<Vertex>(meshes) {

    init {
        for (i in meshes.indices) {
            val mesh = meshes[i]
            val meshMinMax = mesh.minMax
            StaticMesh.calculateMinMax(minMax.min, minMax.max, meshMinMax)
        }
    }
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

//    val uniqueVertices: Set<Vertex> = compiledVertices.toSet()
//    val uniqueIndices = IntArrayList().apply {
//        meshes.map { mesh ->
//            val vertices: List<Vertex> = mesh.faces.flatMap { face ->
//                (0..2).map { i ->
//                    Vertex(face.positions[i], face.texCoords[i], face.normals[i])
//                }
//            }
//            vertices.forEach { add(uniqueVertices.indexOf(it)) }
//        }
//    }
//  Use this and change baseVertex to model base vertex
//    override val indices = uniqueIndices.toArray()
//    override val verticesStructArrayPacked = StructArray(meshes.sumBy { it.uniqueVertices.size }) { VertexStructPacked() }.apply {
//        var counter = 0
//        for(mesh in meshes) {
//            for(vertex in mesh.uniqueVertices) {
//                val (position, texCoord, normal) = vertex
//                val target = getAtIndex(counter)
//                target.position.set(position)
//                target.texCoord.set(texCoord)
//                target.normal.set(normal)
//                counter++
//            }
//        }
//    }
    override val verticesStructArrayPacked = StructArray(meshes.sumBy { it.uniqueVertices.size }) { VertexStructPacked() }.apply {
        var counter = 0
        for(mesh in meshes) {
            for(vertex in mesh.uniqueVertices) {
                val (position, texCoord, normal) = vertex
                val target = getAtIndex(counter)
                target.position.set(position)
                target.texCoord.set(texCoord)
                target.normal.set(normal)
                counter++
            }
        }
    }

    fun getMesh(i: Int): Mesh<Vertex> {
        return meshes[i]
    }

    override fun toString(): String {
        return "StaticModel($path)"
    }

    override fun getMinMax(transform: Transform<*>): AABB {
        return getMinMaxWorld(transform)
    }
}
