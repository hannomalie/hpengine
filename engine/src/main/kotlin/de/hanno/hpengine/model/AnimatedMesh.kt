package de.hanno.hpengine.model

import AnimatedVertexStruktPackedImpl.Companion.sizeInBytes
import AnimatedVertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.model.animation.Animation
import de.hanno.hpengine.model.animation.AnimationController
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.AnimatedVertex
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.AABBData
import de.hanno.hpengine.transform.AABBData.Companion.getSurroundingAABB
import de.hanno.hpengine.transform.SimpleSpatial
import de.hanno.hpengine.transform.absoluteMaximum
import de.hanno.hpengine.transform.absoluteMinimum
import de.hanno.struct.StructArray
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import struktgen.TypedBuffer
import java.io.File

class AnimatedMesh(
    override var name: String,
    override val vertices: List<AnimatedVertex>,
    override val faces: List<IndexedFace>,
    val aabb: AABBData,
    override var material: Material
) : Mesh<AnimatedVertex> {
    var model: AnimatedModel? = null

    override val indexBufferValues = StructArray(faces.size * 3) { IntStruct() }.apply {
        faces.forEachIndexed { index, face ->
            this.getAtIndex(3 * index).value = face.a
            this.getAtIndex(3 * index + 1).value = face.b
            this.getAtIndex(3 * index + 2).value = face.c
        }
    }

    override val triangleCount: Int
        get() = faces.size


    override val spatial: SimpleSpatial = SimpleSpatial(AABB(Vector3f(), Vector3f())).apply {
        boundingVolume.localAABB = calculateBoundingAABB(Mesh.IDENTITY, vertices, faces)
    }

    fun calculateAABB(modelMatrix: Matrix4f?) = calculateBoundingAABB(modelMatrix, vertices, faces)

    companion object {
        fun calculateBoundingAABB(
            modelMatrix: Matrix4f?,
            vertices: List<AnimatedVertex>,
            faces: Collection<IndexedFace>
        ): AABBData {
            val min = Vector3f(absoluteMaximum)
            val max = Vector3f(absoluteMinimum)

            val positions = vertices.map { it.position } // TODO: Optimization, use vertex array instead of positions
            for (face in faces) {
                val vertices = listOf(positions[face.a], positions[face.b], positions[face.c])

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
    }
}

class AnimatedModel(
    override val file: File,
    meshes: List<AnimatedMesh>,
    val animations: Map<String, Animation>
) : Model<AnimatedVertex>(meshes) {
    override val bytesPerVertex = AnimatedVertexStruktPacked.sizeInBytes
    override val path = file.absolutePath

    init {
        for (mesh in meshes) {
            mesh.model = this
        }
    }

    val animation = animations.entries.first().value // TOOD: Use all animations
    val animationController = AnimationController(animation)

    override val verticesPacked = TypedBuffer(
        BufferUtils.createByteBuffer(meshes.sumBy { it.vertices.size } * AnimatedVertexStruktPacked.sizeInBytes),
        AnimatedVertexStruktPacked.type).apply {
        byteBuffer.run {
            var counter = 0
            for (mesh in meshes) {
                for (vertex in mesh.vertices) {
                    this@apply[counter].run {
                        position.run { set(vertex.position) }
                        texCoord.run { set(vertex.texCoord) }
                        normal.run { set(vertex.normal) }
                        weights.run { set(vertex.weights) }
                        jointIndices.run { set(vertex.jointIndices) }
                    }
                    counter++
                }
            }
        }
    }

    override fun update(deltaSeconds: Float) {
        animationController.update(deltaSeconds)
    }

    // Working with mesh bounding volumes doesnt make sense for animated models as they are heavily transformed
    // and its better to use a relaxed shared volume for the whole model
    override fun getBoundingVolume(transform: Matrix4f, mesh: Mesh<*>) = getBoundingVolume(transform)
    override val boundingVolume: AABB = calculateBoundingVolume()

    override fun calculateBoundingVolume() =
        AABB(meshes.map { it.spatial.boundingVolume.localAABB }.getSurroundingAABB())
}

