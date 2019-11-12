package de.hanno.hpengine.engine.model

import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import org.joml.Vector3f

interface Mesh<T : Bufferable> {
    val vertexBufferValuesArray: FloatArray
    val indexBufferValuesArray: IntArray
    val triangleCount: Int
    var material: SimpleMaterial
    val boundingSphereRadius: Float
    var name: String
    val faces: List<StaticMesh.CompiledFace>
    val indexBufferValues: IntArrayList
    val compiledVertices: List<T>
    val minMax: AABB
        get() = getMinMax(IDENTITY)
    fun putToValueArrays()
    fun getMinMax(transform: Transform<*>): AABB
    fun getCenter(transform: Entity): Vector3f
    fun init(materialManager: MaterialManager)
    companion object {
        val IDENTITY = Transform<Transform<*>>()
        val MAX_WEIGHTS = 4
    }
}
