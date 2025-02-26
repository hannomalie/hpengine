package de.hanno.hpengine.model

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.transform.AABBData
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import java.nio.IntBuffer

interface Mesh<T> {
    val boundingVolume: AABBData

    val triangleCount: ElementCount
    var material: Material
    var name: String
    val vertices: List<T>
    val triangles: List<IndexedTriangle>

    companion object {
        val MAX_WEIGHTS = 4
    }
}

data class CompiledVertex(val position: Vector3f, val texCoords: Vector2f, val normal: Vector3f)

class CompiledFace(val positions: Array<Vector3f>, val texCoords: Array<Vector2f>, val normals: Array<Vector3f>) {
    val vertices = (0..2).map { CompiledVertex(positions[it], texCoords[it], normals[it]) }
}

data class IndexedTriangle(val a: Int, val b: Int, val c: Int)

fun List<IndexedTriangle>.extractIndices(): ByteBuffer = BufferUtils.createByteBuffer(Integer.BYTES * size * 3).apply {
    extractIndices(this.asIntBuffer(), 0)
}

fun List<IndexedTriangle>.extractIndices(target: IntBuffer, baseIndex: Int) = target.apply {
    forEachIndexed { index, face ->
        put(baseIndex + 3 * index, face.a)
        put(baseIndex + 3 * index + 1, face.b)
        put(baseIndex + 3 * index + 2, face.c)
    }
}