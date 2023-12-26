package de.hanno.hpengine.model

import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.Vertex
import de.hanno.hpengine.transform.AABBData
import de.hanno.hpengine.transform.x
import de.hanno.hpengine.transform.y
import de.hanno.hpengine.transform.z
import java.io.Serializable
import java.util.*


class StaticMesh(
    override var name: String = "",
    override val vertices: List<Vertex>,
    override val faces: List<IndexedFace>,
    override var material: Material
) : Serializable, Mesh<Vertex> {

    val uuid = UUID.randomUUID()

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

    override val indexBufferValues = faces.extractIndices()

    override val triangleCount: Int get() = faces.size

    override fun equals(other: Any?): Boolean {
        if (other !is StaticMesh) {
            return false
        }

        val b = other as StaticMesh?

        return b!!.uuid == uuid
    }

    override fun toString(): String {
        return name
    }

    override fun hashCode(): Int {
        return uuid.hashCode()
    }
}
