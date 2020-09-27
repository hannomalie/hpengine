package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Spatial
import de.hanno.struct.Struct
import de.hanno.struct.copyTo
import org.joml.Matrix4f


@JvmOverloads
fun <T: Struct> de.hanno.struct.StructArray<T>.copyTo(target: de.hanno.struct.StructArray<T>, offset: Int, rewindBuffers: Boolean = true) {
    buffer.copyTo(target.buffer, rewindBuffers, slidingWindow.sizeInBytes * offset)
}

abstract class AbstractModel<T>(final override val meshes: List<Mesh<T>>,
                                material: Material) : SimpleSpatial(), Model<T>, Spatial {

    final override val meshIndexCounts = meshes.map { it.indexBufferValues.size }
    val meshIndexSum = meshIndexCounts.sum()

    override var indices = de.hanno.struct.StructArray(meshIndexSum) { IntStruct() }.apply {
        var offsetPerMesh = 0
        meshes.forEach { mesh ->
            mesh.indexBufferValues.copyTo(this, offset = offsetPerMesh)
            offsetPerMesh += mesh.indexBufferValues.size
        }
    }
    override var triangleCount: Int = meshes.sumBy { it.triangleCount }
    override val uniqueVertices: List<T> = meshes.flatMap { it.vertices }

    override var material: Material = material
        set(value) {
            meshes.forEach { it.material = value }
            field = value
        }

    override fun getBoundingVolume(transform: Matrix4f): AABB = super<SimpleSpatial>.getBoundingVolume(transform)
    abstract fun calculateBoundingVolume(): AABB
}
