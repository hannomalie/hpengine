package de.hanno.hpengine.model

import de.hanno.hpengine.model.Mesh.Companion.IDENTITY
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.Vertex
import de.hanno.hpengine.transform.*
import org.joml.*
import java.io.Serializable
import java.util.*


class StaticMesh(
    override var name: String = "",
    override val vertices: List<Vertex>,
    override val faces: List<IndexedFace>,
    override var material: Material
) : Serializable, Mesh<Vertex> {

    val uuid = UUID.randomUUID()

    override val spatial: SimpleSpatial = SimpleSpatial(AABB(Vector3f(), Vector3f())).apply {
        boundingVolume.localAABB = calculateAABB(IDENTITY, vertices.map { it.position }, faces)
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
