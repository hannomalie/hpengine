package de.hanno.hpengine.model

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.BaseVertex
import de.hanno.hpengine.sum
import de.hanno.hpengine.transform.AABB
import java.io.File
import java.nio.IntBuffer


fun Model<*>.putIndexValues(intBuffer: IntBuffer, baseOffset: Int) {
    var meshOffset = 0
    meshes.forEach { mesh ->
        mesh.triangles.forEachIndexed { index, face ->
            intBuffer.put(baseOffset + meshOffset + 3 * index, face.a)
            intBuffer.put(baseOffset + meshOffset + 3 * index + 1, face.b)
            intBuffer.put(baseOffset + meshOffset + 3 * index + 2, face.c)
        }
        meshOffset += mesh.triangleCount.value.toInt() * 3
    }
}

// TODO: Why did I make this an array when we already have a list?
sealed class Model<T: BaseVertex>(val _meshes: List<Mesh<T>>) {
    val meshes: Array<Mesh<T>> = _meshes.toTypedArray()

    val meshIndexCounts = meshes.map { ElementCount(it.triangleCount.value * 3) }
    val meshIndexSum = meshIndexCounts.sum()
    val indexedVertices = meshes.flatMap { it.vertices }
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

    val indicesCount: ElementCount = ElementCount(meshes.sumOf { it.triangleCount.value * 3 })

    val indices = IntBuffer.allocate(meshes.sumOf { it.triangles.size * 3 }).apply {
        var counter = 0
        meshes.forEach { mesh ->
            mesh.triangles.forEach { triangle ->
                put(counter++, triangle.a)
                put(counter++, triangle.b)
                put(counter++, triangle.c)
            }
        }
    }
    fun setMaterial(value: Material) {
        meshes.forEach { it.material = value }
    }

    val materials: List<Material> get() = meshes.map { it.material }

    abstract val file: File
    abstract val path: String
    val boundingSphereRadius: Float get() = boundingVolume.boundingSphereRadius
    abstract val boundingVolume: AABB

    val isStatic: Boolean get() = when(this) {
        is AnimatedModel -> false
        is StaticModel -> true
    }
    var isInvertTexCoordY = true
    abstract val bytesPerVertex: Int
}
