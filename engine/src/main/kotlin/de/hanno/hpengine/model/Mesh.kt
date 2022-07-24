package de.hanno.hpengine.model

import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.transform.SimpleSpatial
import de.hanno.hpengine.transform.Transform
import java.nio.ByteBuffer
import java.nio.IntBuffer

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
