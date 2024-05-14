package de.hanno.hpengine.model

import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.BaseVertex
import de.hanno.hpengine.transform.AABB
import org.lwjgl.BufferUtils
import struktgen.api.Strukt
import struktgen.api.TypedBuffer
import java.io.File

// TODO: Why did I make this an array when we already have a list?
sealed class Model<T: BaseVertex>(val _meshes: List<Mesh<T>>) {
    val meshes: Array<Mesh<T>> = _meshes.toTypedArray()

    val meshIndexCounts = meshes.map { it.indexBufferValues.capacity() / Integer.BYTES }
    val meshIndexSum = meshIndexCounts.sum()

    var triangleCount: Int = meshes.sumOf { it.triangleCount }
    val uniqueVertices: List<T> = meshes.flatMap { it.vertices }

    var indices = BufferUtils.createByteBuffer(Integer.BYTES * meshIndexSum).apply {
        var offsetPerMesh = 0
        meshes.forEach { mesh ->
            mesh.indexBufferValues.copyTo(this, targetOffsetInBytes = offsetPerMesh)
            offsetPerMesh += mesh.indexBufferValues.capacity()
        }
    }

    fun setMaterial(value: Material) {
        meshes.forEach { it.material = value }
    }

    val materials: List<Material> get() = meshes.map { it.material }

    abstract val file: File
    abstract val path: String
    abstract val verticesPacked: TypedBuffer<out Strukt>
    val boundingSphereRadius: Float get() = boundingVolume.boundingSphereRadius
    abstract val boundingVolume: AABB

    val isStatic: Boolean get() = when(this) {
        is AnimatedModel -> false
        is StaticModel -> true
    }
    var isInvertTexCoordY = true
    abstract val bytesPerVertex: Int
}
