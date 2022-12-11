package de.hanno.hpengine.model

import de.hanno.hpengine.Transform
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.transform.SimpleSpatial
import org.joml.Vector2f
import org.joml.Vector3f
import java.nio.ByteBuffer

interface Mesh<T> {
    val indexBufferValues: ByteBuffer

    val triangleCount: Int
    var material: Material
    var name: String
    val vertices: List<T>
    val faces: List<IndexedFace>
    val spatial: SimpleSpatial

    companion object {
        val IDENTITY: Transform = Transform()
        val MAX_WEIGHTS = 4
    }
}

data class CompiledVertex(val position: Vector3f, val texCoords: Vector2f, val normal: Vector3f)

class CompiledFace(val positions: Array<Vector3f>, val texCoords: Array<Vector2f>, val normals: Array<Vector3f>) {
    val vertices = (0..2).map { CompiledVertex(positions[it], texCoords[it], normals[it]) }
}

data class IndexedFace(val a: Int, val b: Int, val c: Int)
