package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform

interface Model<T> {

    val meshes: List<Mesh<T>>

    val boundingSphereRadius: Float

    val triangleCount: Int

    val vertexBufferValuesArray: FloatArray

    val indices: IntArray

    val minMax: AABB

    val meshIndices: Array<IntArray>

    val compiledVertices: List<T>

    val isStatic: Boolean
        get() = true

    val isInvertTexCoordY: Boolean
        get() = true

    fun setMaterial(material: Material) {
        for (mesh in meshes) {
            mesh.material = material
        }
    }

    fun getMinMax(transform: Transform<*>): AABB

    fun getBoundingSphereRadius(mesh: Mesh<*>): Float {
        return mesh.boundingSphereRadius
    }

    fun getMinMax(transform: Transform<*>, mesh: Mesh<*>): AABB {
        return mesh.getMinMax(transform)
    }

    fun getMinMax(mesh: Mesh<*>): AABB {
        return mesh.minMax
    }
}
