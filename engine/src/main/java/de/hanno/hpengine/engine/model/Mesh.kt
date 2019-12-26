package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.struct.StructArray
import org.joml.Vector3f

interface Mesh<T> {
    val indexBufferValues: StructArray<IntStruct>

    val triangleCount: Int
    var material: Material
    val boundingSphereRadius: Float
    var name: String
    val vertices: List<T>
    val faces: List<IndexedFace>
    val minMax: AABB
        get() = getMinMax(IDENTITY)

    fun getMinMax(transform: Transform<*>): AABB
    fun getCenter(transform: Entity): Vector3f
    companion object {
        val IDENTITY = Transform<Transform<*>>()
        val MAX_WEIGHTS = 4
    }
}
