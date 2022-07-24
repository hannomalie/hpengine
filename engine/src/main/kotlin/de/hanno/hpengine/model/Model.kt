package de.hanno.hpengine.model

import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.SimpleSpatial
import de.hanno.hpengine.transform.Spatial
import de.hanno.hpengine.transform.boundingSphereRadius
import de.hanno.hpengine.buffers.copyTo
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import struktgen.TypedBuffer
import struktgen.api.Strukt
import java.io.File


sealed class Model<T>(val _meshes: List<Mesh<T>>) : SimpleSpatial(), Spatial {
    val meshes: Array<Mesh<T>> = _meshes.toTypedArray()

    val meshIndexCounts = meshes.map { it.indexBufferValues.capacity() / Integer.BYTES }
    val meshIndexSum = meshIndexCounts.sum()

    var triangleCount: Int = meshes.sumBy { it.triangleCount }
    val uniqueVertices: List<T> = meshes.flatMap { it.vertices }

    var indices = BufferUtils.createByteBuffer(Integer.BYTES * meshIndexSum).apply {
        var offsetPerMesh = 0
        meshes.forEach { mesh ->
            mesh.indexBufferValues.copyTo(this, targetOffset = offsetPerMesh)
            offsetPerMesh += mesh.indexBufferValues.capacity() / Integer.BYTES
        }
    }

    fun setMaterial(value: Material) {
        meshes.forEach { it.material = value }
    }

    val materials: List<Material> get() = meshes.map { it.material }

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
