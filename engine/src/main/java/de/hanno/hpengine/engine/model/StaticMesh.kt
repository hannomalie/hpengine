package de.hanno.hpengine.engine.model

import com.carrotsearch.hppc.FloatArrayList
import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
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
import java.util.ArrayList
import java.util.UUID
import java.util.logging.Level

class StaticMesh(override var name: String = "",
                 val positions: MutableList<Vector3f> = ArrayList(),
                 val texCoords: MutableList<Vector2f> = ArrayList(),
                 val normals: MutableList<Vector3f> = ArrayList()) : SimpleSpatial(), Serializable, Mesh<Bufferable> {

    val uuid = UUID.randomUUID()

    val indexedFaces: MutableList<Face> = ArrayList()
    private val compiledFaces = ArrayList<CompiledFace>()
    override lateinit var material: SimpleMaterial
    override val indexBufferValues = IntArrayList()
    private val vertexBufferValues = FloatArrayList()
    override lateinit var indexBufferValuesArray: IntArray
    private val valuesPerVertex = DataChannels.totalElementsPerVertex(ModelComponent.DEFAULTCHANNELS)
    override val compiledVertices = ArrayList<Vertex>()
    override val minMax = AABB(Vector3f(), Vector3f())

    override lateinit var vertexBufferValuesArray: FloatArray

    override val faces: List<CompiledFace>
        get() = compiledFaces

    override val triangleCount: Int
        get() = indexBufferValues.size() / 3

    override fun init(materialManager: MaterialManager) {

        if (!::material.isInitialized) {
            LOGGER.log(Level.INFO, "No material found for mesh " + this.name + "!!!")
            material = materialManager.defaultMaterial
        }
        compiledVertices.clear()

        val values = FloatArrayList(indexedFaces.size * valuesPerVertex)

        for (i in indexedFaces.indices) {
            val face = indexedFaces[i]

            val referencedVertices = face.vertices
            val referencedNormals = face.normalIndices
            val referencedTexcoords = face.textureCoordinateIndices
            val compiledPositions = arrayOf(Vector3f(), Vector3f(), Vector3f())
            val compiledTexCoords = arrayOf(Vector2f(), Vector2f(), Vector2f())
            val compiledNormals = arrayOf(Vector3f(), Vector3f(),Vector3f())

            for (j in 0..2) {
                val referencedVertex = positions[referencedVertices[j] - 1]
                compiledPositions[j] = referencedVertex
                var referencedTexcoord = Vector2f(0f, 0f)
                try {
                    referencedTexcoord = texCoords[referencedTexcoords[j] - 1]
                } catch (e: Exception) {
                }

                compiledTexCoords[j] = referencedTexcoord
                val referencedNormal = normals[referencedNormals[j] - 1]
                compiledNormals[j] = referencedNormal

                values.add(referencedVertex.x)
                values.add(referencedVertex.y)
                values.add(referencedVertex.z)
                values.add(referencedTexcoord.x)
                values.add(referencedTexcoord.y)
                values.add(referencedNormal.x)
                values.add(referencedNormal.y)
                values.add(referencedNormal.z)


                compiledVertices.add(Vertex("Vertex", referencedVertex, referencedTexcoord, referencedNormal))

            }
            compiledFaces.add(CompiledFace(compiledPositions, compiledTexCoords, compiledNormals))
        }

        val uniqueVertices = ArrayList<CompiledVertex>()
        for (currentFace in compiledFaces) {
            for (i in 0..2) {
                val currentVertex = currentFace.vertices[i]
                run {
                    uniqueVertices.add(currentVertex)
                    positions.add(currentVertex.position)
                    texCoords.add(currentVertex.texCoords)
                    normals.add(currentVertex.normal)
                    indexBufferValues.add(uniqueVertices.size - 1)
                }
            }
        }

        putToValueArrays()
        calculateMinMax(null, minMax.min, minMax.max, compiledFaces)
    }

    override fun putToValueArrays() {
        vertexBufferValues.clear()
        vertexBufferValuesArray = FloatArray(compiledFaces.size * 3 * valuesPerVertex)
        for (currentFace in compiledFaces) {
            for (i in 0..2) {
                vertexBufferValues.add(*currentFace.vertices[i].asFloats())
            }
        }
        vertexBufferValuesArray = vertexBufferValues.toArray()

        indexBufferValuesArray = IntArray(indexBufferValues.size())
        for (indexIndex in 0 until indexBufferValues.size()) {
            indexBufferValuesArray[indexIndex] = indexBufferValues.get(indexIndex)
        }
    }

    override fun getMinMax(transform: Transform<*>): AABB {
        if (!isClean(transform)) {
            calculateMinMax(transform, minMax.min, minMax.max, compiledFaces)
        }
        return super.getMinMaxWorld(transform)
    }

    override fun getCenter(transform: Entity): Vector3f {
        return super.getCenterWorld(transform)
    }

    override fun getCenterWorld(transform: Transform<*>): Vector3f {
        if (!isClean(transform)) {
            calculateMinMax(transform, minMax.min, minMax.max, compiledFaces)
        }
        return super.getCenterWorld(transform)
    }

    override fun getMinMaxWorld(transform: Transform<*>): AABB {
        return getMinMax(transform)
    }

    override fun getBoundingSphereRadius(transform: Transform<*>): Float {
        if (!isClean(transform)) {
            calculateMinMax(transform, minMax.min, minMax.max, compiledFaces)
        }
        return super.getBoundingSphereRadius(transform)
    }

    class CompiledVertex(val position: Vector3f, val texCoords: Vector2f, val normal: Vector3f) {

        override fun equals(other: Any?): Boolean {
            if (other !is CompiledVertex) {
                return false
            }

            val otherVertex = other as CompiledVertex?
            return this.position == otherVertex!!.position &&
                    this.texCoords == otherVertex.texCoords &&
                    this.normal == otherVertex.normal
        }

        fun asFloats(): FloatArray {
            return floatArrayOf(position.x, position.y, position.z, texCoords.x, texCoords.y, normal.x, normal.y, normal.z)
        }
    }

    class CompiledFace(val positions: Array<Vector3f>, texCoords: Array<Vector2f>, normal: Array<Vector3f>) {

        val vertices = (0..2).map { CompiledVertex(positions[it], texCoords[it], normal[it]) }

        override fun equals(other: Any?): Boolean {
            if (other is CompiledFace) {
                val otherFace = other as CompiledFace?
                return this.vertices == otherFace!!.vertices
            }
            return false
        }
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

        fun calculateMinMax(modelMatrix: Matrix4f?, min: Vector3f, max: Vector3f, compiledFaces: List<CompiledFace>) {
            min.set(java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE)
            max.set(-java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE)

            for (i in compiledFaces.indices) {
                val face = compiledFaces[i]

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
