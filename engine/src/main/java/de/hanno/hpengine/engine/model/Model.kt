package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.struct.Struct
import de.hanno.struct.StructArray
import org.joml.Matrix4f
import java.io.File

interface Model<T> {
    val file: File

    val path: String

    val verticesStructArray: StructArray<out Struct>

    val verticesStructArrayPacked: StructArray<out Struct>

    val meshes: List<Mesh<T>>

    val boundingSphereRadius: Float

    val triangleCount: Int

    val minMax: AABB

    val indices: StructArray<IntStruct>

    val uniqueVertices: List<T>

    val isStatic: Boolean
        get() = true

    val isInvertTexCoordY: Boolean
        get() = true

    val bytesPerVertex: Int

    fun getMinMax(transform: Matrix4f): AABB {
        minMax.recalculate(transform)
        return minMax
    }

    fun getBoundingSphereRadius(mesh: Mesh<*>): Float {
        return mesh.spatial.boundingSphereRadius
    }

    fun getMinMax(transform: Matrix4f, mesh: Mesh<*>): AABB {
        return mesh.spatial.getMinMax(transform)
    }

    fun getMinMax(mesh: Mesh<*>): AABB {
        return mesh.spatial.minMax
    }
    var material: Material
    val meshIndexCounts: List<Int>
}
