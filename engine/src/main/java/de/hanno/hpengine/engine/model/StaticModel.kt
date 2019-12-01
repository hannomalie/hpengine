package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.scene.Vertex
import de.hanno.hpengine.engine.scene.VertexStruct
import de.hanno.hpengine.engine.scene.VertexStructPacked
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.struct.StructArray
import org.joml.Vector2f
import org.joml.Vector3f
import java.lang.IllegalStateException

class StaticModel(private val path: String,
                  val vertices: List<Vector3f>,
                  val texCoords: List<Vector2f>,
                  val normals: List<Vector3f>,
                  meshes: List<StaticMesh>) : AbstractModel<Vertex>(meshes) {

    init {
        for (i in meshes.indices) {
            val mesh = meshes[i]
            val meshMinMax = mesh.minMax
            StaticMesh.calculateMinMax(minMax.min, minMax.max, meshMinMax)
        }
    }
    override val bytesPerVertex = VertexStructPacked.sizeInBytes

    override val verticesStructArray = StructArray(compiledVertices.size) { VertexStruct() }.apply {
        for (i in compiledVertices.indices) {
            val vertex = compiledVertices[i]
            val (position, texCoord, normal) = vertex
            val target = getAtIndex(i)
            target.position.set(position)
            target.texCoord.set(texCoord)
            target.normal.set(normal)
        }
    }

    override val verticesStructArrayPacked = StructArray(meshes.sumBy { it.compiledVertices.size }) { VertexStructPacked() }.apply {
        var counter = 0
        for(mesh in meshes) {
            for(vertex in mesh.compiledVertices) {
                val (position, texCoord, normal) = vertex
                val target = getAtIndex(counter)
                target.position.set(position)
                target.texCoord.set(texCoord)
                target.normal.set(normal)
                counter ++
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
