package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Spatial

abstract class AbstractModel<T>(final override val meshes: List<Mesh<T>>,
                                material: Material) : SimpleSpatial(), Model<T>, Spatial {
    override var meshIndices: Array<IntArray> = meshes.map { it.indexBufferValues }.toTypedArray()
    override var triangleCount: Int = meshes.sumBy { it.triangleCount }
    override val uniqueVertices: List<T> = meshes.flatMap { it.uniqueVertices }

    override val indices: IntArray = meshes.fold(mutableListOf<Int>()) { list, it ->
        list.apply { addAll(it.indexBufferValues.toList()) }
    }.toIntArray() // TODO Make more pretty

    override val vertexBufferValuesArray: FloatArray = meshes.fold(mutableListOf<Float>()) { list, it ->
        list.apply { addAll(it.vertexBufferValues.toList()) }
    }.toFloatArray() // TODO Make more pretty

    override var material: Material = material
        set(value) {
            meshes.forEach { it.material = value }
            field = value
        }
}
