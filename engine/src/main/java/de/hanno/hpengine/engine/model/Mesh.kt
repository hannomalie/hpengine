package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import org.joml.Vector3f

interface Mesh<T> {
    val vertexBufferValues: FloatArray
    val indexBufferValues: IntArray

    val triangleCount: Int
    var material: Material
    val boundingSphereRadius: Float
    var name: String
    val faces: List<StaticMesh.CompiledFace>
    val compiledVertices: List<T>
    val uniqueVertices: Set<T>
    val minMax: AABB
        get() = getMinMax(IDENTITY)

    fun getMinMax(transform: Transform<*>): AABB
    fun getCenter(transform: Entity): Vector3f
    companion object {
        val IDENTITY = Transform<Transform<*>>()
        val MAX_WEIGHTS = 4
    }
}
