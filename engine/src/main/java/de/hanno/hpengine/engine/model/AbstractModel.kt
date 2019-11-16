package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Spatial

abstract class AbstractModel<T>(final override val meshes: List<Mesh<T>>) : SimpleSpatial(), Model<T>, Spatial {
    override var meshIndices: Array<IntArray> = meshes.map { it.indexBufferValues }.toTypedArray()
    override var triangleCount: Int = meshes.sumBy { it.triangleCount }
    override val compiledVertices: List<T> = meshes.flatMap { it.compiledVertices }.toList()
    override val indices: IntArray = meshes.fold(mutableListOf<Int>()) { list, it ->
        list.apply { addAll(it.indexBufferValues.toList()) }
    }.toIntArray() // TODO Make more pretty

    override val vertexBufferValuesArray: FloatArray = meshes.fold(mutableListOf<Float>()) { list, it ->
        list.apply { addAll(it.vertexBufferValues.toList()) }
    }.toFloatArray() // TODO Make more pretty

    init {
        for (i in meshes.indices) {
            val mesh = meshes[i]
            val meshMinMax = mesh.minMax
            StaticMesh.calculateMinMax(minMax.min, minMax.max, meshMinMax)
        }
    }
}
