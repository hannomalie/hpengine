package de.hanno.hpengine.model

import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.Vertex
import de.hanno.hpengine.toCount
import de.hanno.hpengine.transform.AABBData
import de.hanno.hpengine.transform.x
import de.hanno.hpengine.transform.y
import de.hanno.hpengine.transform.z
import java.io.Serializable
import java.util.*


class StaticMesh(
    override var name: String = "",
    override val vertices: List<Vertex>,
    override val triangles: List<IndexedTriangle>,
    override var material: Material
) : Serializable, Mesh<Vertex> {

    val uuid = UUID.randomUUID()

    override val boundingVolume = AABBData().apply {
        vertices.forEach {
            min.x = minOf(min.x, it.position.x)
            min.y = minOf(min.y, it.position.y)
            min.z = minOf(min.z, it.position.z)

            max.x = maxOf(max.x, it.position.x)
            max.y = maxOf(max.y, it.position.y)
            max.z = maxOf(max.z, it.position.z)
        }
    }

    override val triangleCount = triangles.size.toCount()

    override fun equals(other: Any?): Boolean {
        if (other !is StaticMesh) {
            return false
        }

        return other.uuid == uuid
    }

    override fun toString(): String {
        return name
    }

    override fun hashCode(): Int {
        return uuid.hashCode()
    }
}
