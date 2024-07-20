package de.hanno.hpengine.model

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.buffers.copyTo
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.BaseVertex
import de.hanno.hpengine.sum
import de.hanno.hpengine.transform.AABB
import org.lwjgl.BufferUtils
import struktgen.api.Strukt
import struktgen.api.TypedBuffer
import java.io.File

// TODO: Why did I make this an array when we already have a list?
sealed class Model<T: BaseVertex>(val _meshes: List<Mesh<T>>) {
    val meshes: Array<Mesh<T>> = _meshes.toTypedArray()

    val meshIndexCounts = meshes.map { ElementCount(it.indexBufferValues.capacity() / Integer.BYTES) }
    val meshIndexSum = meshIndexCounts.sum()
    val indexedVertices by lazy {
        meshes.flatMap { it.vertices }
    }
    val unindexedVertices by lazy {
        buildList {
            for (mesh in meshes) {
                for (triangle in mesh.triangles) {
                    add(mesh.vertices[triangle.a])
                    add(mesh.vertices[triangle.b])
                    add(mesh.vertices[triangle.c])
                }
            }
        }
    }

    var triangleCount = ElementCount(meshes.sumOf { it.triangleCount.value })
    val uniqueVertices: List<T> = meshes.flatMap { it.vertices }

    var indices = BufferUtils.createByteBuffer(Integer.BYTES * meshIndexSum.toInt()).apply {
        var offsetPerMesh = SizeInBytes(0)
        meshes.forEach { mesh ->
            mesh.indexBufferValues.copyTo(this, targetOffsetInBytes = offsetPerMesh)
            offsetPerMesh += SizeInBytes(mesh.indexBufferValues.capacity())
        }
    }
    val indicesCount: ElementCount get() = ElementCount(indices.capacity() / Integer.BYTES)

    fun setMaterial(value: Material) {
        meshes.forEach { it.material = value }
    }

    val materials: List<Material> get() = meshes.map { it.material }

    abstract val file: File
    abstract val path: String
    abstract val verticesPacked: TypedBuffer<out Strukt>
    abstract val unindexedVerticesPacked: TypedBuffer<out Strukt>
    val boundingSphereRadius: Float get() = boundingVolume.boundingSphereRadius
    abstract val boundingVolume: AABB

    val isStatic: Boolean get() = when(this) {
        is AnimatedModel -> false
        is StaticModel -> true
    }
    var isInvertTexCoordY = true
    abstract val bytesPerVertex: Int
}
