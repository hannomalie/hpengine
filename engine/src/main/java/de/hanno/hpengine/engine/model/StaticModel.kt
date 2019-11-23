package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.scene.Vertex
import de.hanno.hpengine.engine.scene.VertexStruct
import de.hanno.hpengine.engine.scene.VertexStructPacked
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.struct.StructArray
import org.joml.Vector2f
import org.joml.Vector3f

class StaticModel(private val path: String,
                  val vertices: List<Vector3f>,
                  val texCoords: List<Vector2f>,
                  val normals: List<Vector3f>,
                  meshes: List<Mesh<Vertex>>) : AbstractModel<Vertex>(meshes) {

    override val bytesPerVertex = VertexStruct.sizeInBytes

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
