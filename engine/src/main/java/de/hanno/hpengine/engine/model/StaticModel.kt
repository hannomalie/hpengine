package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import org.joml.Vector2f
import org.joml.Vector3f

import java.util.ArrayList

class StaticModel<T>(private val path: String,
                     val vertices: List<Vector3f>,
                     val texCoords: List<Vector2f>,
                     val normals: List<Vector3f>,
                     meshes: List<Mesh<T>>) : AbstractModel<T>(meshes) {

    fun getMesh(i: Int): Mesh<*> {
        return meshes[i]
    }

    override fun toString(): String {
        return "StaticModel($path)"
    }

    override fun getMinMax(transform: Transform<*>): AABB {
        return getMinMaxWorld(transform)
    }
}
