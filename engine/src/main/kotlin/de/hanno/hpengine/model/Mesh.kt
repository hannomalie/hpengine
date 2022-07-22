package de.hanno.hpengine.model

import de.hanno.hpengine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.transform.SimpleSpatial
import de.hanno.hpengine.transform.Transform
import de.hanno.struct.StructArray

interface Mesh<T> {
    val indexBufferValues: StructArray<IntStruct>

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
