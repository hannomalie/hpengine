package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.Vertex
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.log.ConsoleLogger.getLogger
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.Serializable
import java.util.UUID


data class IndexedFace(val a: Int, val b: Int, val c: Int)
class StaticMesh(override var name: String = "",
                 override val vertices: List<Vertex>,
                 override val faces: List<IndexedFace>,
                 override var material: Material) : SimpleSpatial(), Serializable, Mesh<Vertex> {

    val uuid = UUID.randomUUID()

    override val minMax = AABB(Vector3f(), Vector3f())

    init {
        calculateMinMax(null, minMax.min, minMax.max, vertices, faces)
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

    override fun getMinMax(transform: Transform<*>): AABB {
        if (!isClean(transform)) {
            calculateMinMax(transform, minMax.min, minMax.max, vertices, faces)
        }
        return super.getMinMaxWorld(transform)
    }

    override fun getCenter(transform: Entity): Vector3f {
        return super.getCenterWorld(transform)
    }

    override fun getCenterWorld(transform: Transform<*>): Vector3f {
        if (!isClean(transform)) {
            calculateMinMax(transform, minMax.min, minMax.max, vertices, faces)
        }
        return super.getCenterWorld(transform)
    }

    override fun getMinMaxWorld(transform: Transform<*>): AABB {
        return getMinMax(transform)
    }

    override fun getBoundingSphereRadius(transform: Transform<*>): Float {
        if (!isClean(transform)) {
            calculateMinMax(transform, minMax.min, minMax.max, vertices, faces)
        }
        return super.getBoundingSphereRadius(transform)
    }

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

    companion object {
        private val LOGGER = getLogger()

        private const val serialVersionUID = 1L

        fun calculateMinMax(modelMatrix: Matrix4f?, min: Vector3f, max: Vector3f,
                            vertices: Collection<Vertex>, faces: Collection<IndexedFace>) {
            min.set(java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE)
            max.set(-java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE)

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

        }
        fun calculateMinMax(modelMatrix: Matrix4f?, min: Vector3f, max: Vector3f, faces: List<CompiledFace>) {
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
        fun calculateMinMax(targetMin: Vector3f, targetMax: Vector3f, candidate: AABB) {
            targetMin.x = if (candidate.min.x < targetMin.x) candidate.min.x else targetMin.x
            targetMin.y = if (candidate.min.y < targetMin.y) candidate.min.y else targetMin.y
            targetMin.z = if (candidate.min.z < targetMin.z) candidate.min.z else targetMin.z

            targetMax.x = if (candidate.max.x > targetMax.x) candidate.max.x else targetMax.x
            targetMax.y = if (candidate.max.y > targetMax.y) candidate.max.y else targetMax.y
            targetMax.z = if (candidate.max.z > targetMax.z) candidate.max.z else targetMax.z
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

        fun calculateMinMax(transform: Matrix4f, min: Vector3f, max: Vector3f, current: AABB) {
            current.min = transform.transformPosition(current.min)
            current.max = transform.transformPosition(current.max)
            min.x = if (current.min.x < min.x) current.min.x else min.x
            min.y = if (current.min.y < min.y) current.min.y else min.y
            min.z = if (current.min.z < min.z) current.min.z else min.z

            max.x = if (current.max.x > max.x) current.max.x else max.x
            max.y = if (current.max.y > max.y) current.max.y else max.y
            max.z = if (current.max.z > max.z) current.max.z else max.z
        }

        fun getBoundingSphereRadius(target: Vector3f, min: Vector3f, max: Vector3f): Float {
            return target.set(max).sub(min).mul(0.5f).length()
        }

        fun getBoundingSphereRadius(target: Vector3f, min: Vector4f, max: Vector4f): Float {
            return getBoundingSphereRadius(target, Vector3f(min.x, min.y, min.z), Vector3f(max.x, max.y, max.z))
        }
    }
}