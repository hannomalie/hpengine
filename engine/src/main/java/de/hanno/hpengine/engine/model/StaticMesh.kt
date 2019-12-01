package de.hanno.hpengine.engine.model

import com.carrotsearch.hppc.FloatArrayList
import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
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
import java.lang.IllegalStateException
import java.util.ArrayList
import java.util.UUID


data class IndexedFace(val a: Int, val b: Int, val c: Int)
class StaticMesh(override var name: String = "",
                 modelPositions: List<Vector3f>,
                 modelTexCoords: List<Vector2f>,
                 modelNormals: List<Vector3f>,
                 multiIndexedFaces: List<Face>,
                 override var material: Material) : SimpleSpatial(), Serializable, Mesh<Vertex> {

    val uuid = UUID.randomUUID()

    override val compiledVertices = ArrayList<Vertex>()
    override val uniqueVertices = mutableSetOf<Vertex>()
    override val faces = ArrayList<CompiledFace>()
    private val indexedFaces = ArrayList<IndexedFace>()
    private val valuesPerVertex = DataChannels.totalElementsPerVertex(ModelComponent.DEFAULTCHANNELS)
    override val minMax = AABB(Vector3f(), Vector3f())

    init {
        for (face in multiIndexedFaces) {

            val compiledPositions = arrayOf(Vector3f(), Vector3f(), Vector3f())
            val compiledTexCoords = arrayOf(Vector2f(), Vector2f(), Vector2f())
            val compiledNormals = arrayOf(Vector3f(), Vector3f(), Vector3f())

            val vertexIndices = (0..2).map { j ->
                val referencedVertex = modelPositions[face.vertices[j] - 1] // obj is index 1 based
                compiledPositions[j] = referencedVertex
                var referencedTexcoord = Vector2f(0f, 0f)
                try {
                    referencedTexcoord = modelTexCoords[face.textureCoordinateIndices[j] - 1] // obj is index 1 based
                } catch (e: Exception) {
                }

                compiledTexCoords[j] = referencedTexcoord
                val referencedNormal = modelNormals[face.normalIndices[j] - 1] // obj is index 1 based
                compiledNormals[j] = referencedNormal

                val element = Vertex(referencedVertex, referencedTexcoord, referencedNormal)
                uniqueVertices.add(element)
                compiledVertices.add(element)
                uniqueVertices.indexOf(element)
            }
            faces.add(CompiledFace(compiledPositions, compiledTexCoords, compiledNormals))
            indexedFaces.add(IndexedFace(vertexIndices[0], vertexIndices[1], vertexIndices[2]))

        }

        calculateMinMax(null, minMax.min, minMax.max, faces)
    }

    override val indexBufferValues = (0 until faces.size * 3).map { it }.toIntArray()
//    override val indexBufferValues = IntArrayList().apply {
//        indexedFaces.forEach { face ->
//            add(face.a)
//            add(face.b)
//            add(face.c)
//        }
//    }.toArray()

//    override val vertexBufferValues = FloatArrayList().apply {
//        faces.forEach { currentFace ->
//            (0..2).forEach { vertexIndex ->
//                add(*currentFace.vertices[vertexIndex].asFloats())
//            }
//        }
//    }.toArray()
    override val vertexBufferValues = FloatArrayList().apply {
        uniqueVertices.forEach { vertex ->
            add(vertex.position.x)
            add(vertex.position.y)
            add(vertex.position.z)
            add(vertex.texCoord.x)
            add(vertex.texCoord.y)
            add(vertex.normal.x)
            add(vertex.normal.y)
            add(vertex.normal.z)
        }
    }.toArray()

    override val triangleCount: Int
        get() = faces.size

    override fun getMinMax(transform: Transform<*>): AABB {
        if (!isClean(transform)) {
            calculateMinMax(transform, minMax.min, minMax.max, faces)
        }
        return super.getMinMaxWorld(transform)
    }

    override fun getCenter(transform: Entity): Vector3f {
        return super.getCenterWorld(transform)
    }

    override fun getCenterWorld(transform: Transform<*>): Vector3f {
        if (!isClean(transform)) {
            calculateMinMax(transform, minMax.min, minMax.max, faces)
        }
        return super.getCenterWorld(transform)
    }

    override fun getMinMaxWorld(transform: Transform<*>): AABB {
        return getMinMax(transform)
    }

    override fun getBoundingSphereRadius(transform: Transform<*>): Float {
        if (!isClean(transform)) {
            calculateMinMax(transform, minMax.min, minMax.max, faces)
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
