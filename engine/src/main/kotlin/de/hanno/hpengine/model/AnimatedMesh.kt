package de.hanno.hpengine.model

import AnimatedVertexStruktPackedImpl.Companion.sizeInBytes
import AnimatedVertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.model.animation.Animation
import de.hanno.hpengine.model.animation.AnimationController
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.AnimatedVertex
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexStruktPacked
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.AABBData
import de.hanno.hpengine.transform.AABBData.Companion.getSurroundingAABB
import org.lwjgl.BufferUtils
import struktgen.api.TypedBuffer
import struktgen.api.forIndex
import java.io.File
import java.nio.ByteBuffer

class AnimatedMesh(
    override var name: String,
    override val vertices: List<AnimatedVertex>,
    override val triangles: List<IndexedTriangle>,
    val aabb: AABBData,
    override var material: Material
) : Mesh<AnimatedVertex> {
    var model: AnimatedModel? = null

    override val indexBufferValues = triangles.extractIndices()

    override val triangleCount: Int
        get() = triangles.size

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

class AnimatedModel(
    override val file: File,
    meshes: List<AnimatedMesh>,
    val animations: Map<String, Animation>
) : Model<AnimatedVertex>(meshes) {
    override val bytesPerVertex = AnimatedVertexStruktPacked.sizeInBytes
    override val unindexedVerticesPacked: TypedBuffer<VertexStruktPacked>
        get() = TODO("Not yet implemented")

    override val path = file.absolutePath

    init {
        for (mesh in meshes) {
            mesh.model = this
        }
    }

    val animationController = AnimationController(animations)

    override val verticesPacked = TypedBuffer(
        BufferUtils.createByteBuffer(meshes.sumBy { it.vertices.size } * AnimatedVertexStruktPacked.sizeInBytes),
        AnimatedVertexStruktPacked.type).apply {

        byteBuffer.run {
            var counter = 0
            for (mesh in meshes) {
                for (vertex in mesh.vertices) {
                    this@apply.forIndex(counter) {
                        it.position.set(vertex.position)
                        it.texCoord.set(vertex.texCoord)
                        it.normal.set(vertex.normal)
                        it.weights.set(vertex.weights)
                        it.jointIndices.set(vertex.jointIndices)
                    }
                    counter++
                }
            }
        }
    }

    fun update(deltaSeconds: Float) {
        animationController.update(deltaSeconds)
    }

    override val boundingVolume: AABB = calculateBoundingVolume()

    fun calculateBoundingVolume() = AABB(meshes.map { it.boundingVolume }.getSurroundingAABB())
}

internal fun List<IndexedTriangle>.extractIndices(): ByteBuffer =
    BufferUtils.createByteBuffer(Integer.BYTES * size * 3).apply {
        asIntBuffer().apply {
            forEachIndexed { index, face ->
                put(3 * index, face.a)
                put(3 * index + 1, face.b)
                put(3 * index + 2, face.c)
            }
        }
    }