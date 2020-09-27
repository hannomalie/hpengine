package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.model.Mesh.Companion.IDENTITY
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.Vertex
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.AABBData
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.absoluteMaximum
import de.hanno.hpengine.engine.transform.absoluteMinimum
import de.hanno.hpengine.log.ConsoleLogger.getLogger
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import java.io.Serializable
import java.util.UUID


data class IndexedFace(val a: Int, val b: Int, val c: Int)

class StaticMesh(override var name: String = "",
                 override val vertices: List<Vertex>,
                 override val faces: List<IndexedFace>,
                 override var material: Material
                 ) : Serializable, Mesh<Vertex> {

    val uuid = UUID.randomUUID()

    override val spatial: SimpleSpatial = SimpleSpatial(AABB(Vector3f(), Vector3f())).apply {
        boundingVolume.localAABB = calculateAABB(IDENTITY, vertices, faces)
    }

    override val indexBufferValues = de.hanno.struct.StructArray(faces.size * 3) { IntStruct() }.apply {
        faces.withIndex().forEach { (index, face) ->
            getAtIndex(index*3).value = face.a
            getAtIndex(index*3+1).value = face.b
            getAtIndex(index*3+2).value = face.c
        }
    }

    override val triangleCount: Int
        get() = faces.size

    class CompiledVertex(val position: Vector3f, val texCoords: Vector2f, val normal: Vector3f) {
        fun asFloats(): FloatArray {
            return floatArrayOf(position.x, position.y, position.z, texCoords.x, texCoords.y, normal.x, normal.y, normal.z)
        }
    }

    class CompiledFace(val positions: Array<Vector3f>, val texCoords: Array<Vector2f>, val normals: Array<Vector3f>) {
        val vertices = (0..2).map { CompiledVertex(positions[it], texCoords[it], normals[it]) }
    }

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

    fun calculateAABB(modelMatrix: Matrix4f?) = calculateAABB(modelMatrix, vertices, faces)

    companion object {
        private val LOGGER = getLogger()

        private const val serialVersionUID = 1L

        fun calculateAABB(modelMatrix: Matrix4f?, vertices: Collection<Vertex>, faces: Collection<IndexedFace>): AABBData {
            val min = Vector3f(absoluteMaximum)
            val max = Vector3f(absoluteMinimum)

            val positions = vertices.map { it.position } // TODO: Optimization, use vertex array instead of positions
            for (face in faces) {
                val vertices = listOf(positions[face.a],positions[face.b],positions[face.c])

                        for (j in 0..2) {
                    val positionV3 = vertices[j]
                    val position = Vector4f(positionV3.x, positionV3.y, positionV3.z, 1f)
                    if (modelMatrix != null) {
                        position.mul(modelMatrix)
                    }

                    min.x = if (position.x < min.x) position.x else min.x
                    min.y = if (position.y < min.y) position.y else min.y
                    min.z = if (position.z < min.z) position.z else min.z

                    max.x = if (position.x > max.x) position.x else max.x
                    max.y = if (position.y > max.y) position.y else max.y
                    max.z = if (position.z > max.z) position.z else max.z
                }
            }

            return AABBData(Vector3f(min).toImmutable(), Vector3f(max).toImmutable())
        }
        fun calculateAABB(modelMatrix: Matrix4f?, min: Vector3f, max: Vector3f, faces: List<CompiledFace>) {
            min.set(java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE)
            max.set(-java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE)

            for (i in faces.indices) {
                val face = faces[i]

                val vertices = face.vertices

                for (j in 0..2) {
                    val positionV3 = vertices[j].position
                    val position = Vector4f(positionV3.x, positionV3.y, positionV3.z, 1f)
                    if (modelMatrix != null) {
                        position.mul(modelMatrix)
                    }

                    min.x = if (position.x < min.x) position.x else min.x
                    min.y = if (position.y < min.y) position.y else min.y
                    min.z = if (position.z < min.z) position.z else min.z

                    max.x = if (position.x > max.x) position.x else max.x
                    max.y = if (position.y > max.y) position.y else max.y
                    max.z = if (position.z > max.z) position.z else max.z
                }
            }

        }

        //TODO: Move this away from here
        fun calculateAABB(currentMin: Vector3fc, currentMax: Vector3fc, candidate: AABBData): AABBData {
            val newMin = Vector3f(currentMin).min(candidate.min)
            val newMax = Vector3f(currentMax).max(candidate.max)
            return AABBData(newMin, newMax)
        }

        fun calculateMin(old: Vector3f, candidate: Vector3f) {
            old.x = if (candidate.x < old.x) candidate.x else old.x
            old.y = if (candidate.y < old.y) candidate.y else old.y
            old.z = if (candidate.z < old.z) candidate.z else old.z
        }

        fun calculateMax(old: Vector3f, candidate: Vector3f) {
            old.x = if (candidate.x > old.x) candidate.x else old.x
            old.y = if (candidate.y > old.y) candidate.y else old.y
            old.z = if (candidate.z > old.z) candidate.z else old.z
        }

        fun getBoundingSphereRadius(target: Vector3f, min: Vector3f, max: Vector3f): Float {
            return target.set(max).sub(min).mul(0.5f).length()
        }

        fun getBoundingSphereRadius(target: Vector3f, min: Vector4f, max: Vector4f): Float {
            return getBoundingSphereRadius(target, Vector3f(min.x, min.y, min.z), Vector3f(max.x, max.y, max.z))
        }
    }

}
