package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.hpengine.engine.transform.boundingSphereRadius
import de.hanno.struct.Struct
import de.hanno.struct.copyTo
import org.joml.Matrix4f
import struktgen.TypedBuffer
import struktgen.api.Strukt
import java.io.File


@JvmOverloads
fun <T: Struct> de.hanno.struct.StructArray<T>.copyTo(target: de.hanno.struct.StructArray<T>, offset: Int, rewindBuffers: Boolean = true) {
    buffer.copyTo(target.buffer, rewindBuffers, slidingWindow.sizeInBytes * offset)
}

sealed class Model<T>(val meshes: List<Mesh<T>>,
                      material: Material) : SimpleSpatial(), Spatial {

    val meshIndexCounts = meshes.map { it.indexBufferValues.size }
    val meshIndexSum = meshIndexCounts.sum()

    var triangleCount: Int = meshes.sumBy { it.triangleCount }
    val uniqueVertices: List<T> = meshes.flatMap { it.vertices }

    var indices = de.hanno.struct.StructArray(meshIndexSum) { IntStruct() }.apply {
        var offsetPerMesh = 0
        meshes.forEach { mesh ->
            mesh.indexBufferValues.copyTo(this, offset = offsetPerMesh)
            offsetPerMesh += mesh.indexBufferValues.size
        }
    }

    var material: Material = material
        set(value) {
            meshes.forEach { it.material = value }
            field = value
        }
    abstract val file: File
    abstract val path: String
    abstract val verticesPacked: TypedBuffer<out Strukt>
    val boundingSphereRadius: Float
        get() = boundingVolume.boundingSphereRadius
    abstract override val boundingVolume: AABB

    val isStatic: Boolean
        get() = when(this) {
            is AnimatedModel -> false
            is StaticModel -> true
            else -> throw IllegalStateException() // Hello compiler bug
        }
    var isInvertTexCoordY = true
    abstract val bytesPerVertex: Int

    abstract fun calculateBoundingVolume(): AABB
    fun getBoundingSphereRadius(mesh: Mesh<*>): Float = mesh.spatial.boundingSphereRadius
    open fun getBoundingVolume(transform: Matrix4f, mesh: Mesh<*>): AABB = mesh.spatial.getBoundingVolume(transform)
    fun getBoundingVolume(mesh: Mesh<*>): AABB = mesh.spatial.boundingVolume
}
