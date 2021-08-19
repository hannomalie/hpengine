package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.boundingSphereRadius
import de.hanno.struct.Struct
import de.hanno.struct.StructArray
import org.joml.Matrix4f
import struktgen.TypedBuffer
import struktgen.api.Strukt
import java.io.File

interface Model<T> {
    val file: File

    val path: String

    val verticesPacked: TypedBuffer<out Strukt>

    val meshes: List<Mesh<T>>

    val boundingSphereRadius: Float
        get() = boundingVolume.boundingSphereRadius

    val triangleCount: Int

    val boundingVolume: AABB

    val indices: StructArray<IntStruct>

    val uniqueVertices: List<T>

    val isStatic: Boolean
        get() = true

    val isInvertTexCoordY: Boolean
        get() = true

    val bytesPerVertex: Int

    fun getBoundingVolume(transform: Matrix4f): AABB {
        boundingVolume.recalculate(transform)
        return boundingVolume
    }

    fun getBoundingSphereRadius(mesh: Mesh<*>): Float = mesh.spatial.boundingSphereRadius

    fun getBoundingVolume(transform: Matrix4f, mesh: Mesh<*>): AABB = mesh.spatial.getBoundingVolume(transform)

    fun getBoundingVolume(mesh: Mesh<*>): AABB = mesh.spatial.boundingVolume
    var material: Material
    val meshIndexCounts: List<Int>
}
