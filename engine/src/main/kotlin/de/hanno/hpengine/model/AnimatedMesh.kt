package de.hanno.hpengine.model

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.AnimatedVertex
import de.hanno.hpengine.transform.AABBData

class AnimatedMesh(
    override var name: String,
    override val vertices: List<AnimatedVertex>,
    override val triangles: List<IndexedTriangle>,
    val aabb: AABBData,
    override var material: Material
) : Mesh<AnimatedVertex> {
    override val indexBufferValues = triangles.extractIndices()

    override val triangleCount: ElementCount
        get() = ElementCount(triangles.size)

    override val boundingVolume = AABBData().apply {
        vertices.forEach {
            min.x = minOf(min.x, it.position.x)
            min.y = minOf(min.y, it.position.y)
            min.z = minOf(min.x, it.position.z)

            max.x = maxOf(max.x, it.position.x)
            max.y = maxOf(max.y, it.position.y)
            max.z = maxOf(max.x, it.position.z)
        }
    }
}