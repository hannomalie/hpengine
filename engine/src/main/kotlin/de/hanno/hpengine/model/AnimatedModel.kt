package de.hanno.hpengine.model

import AnimatedVertexStruktPackedImpl.Companion.sizeInBytes
import AnimatedVertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.model.animation.Animation
import de.hanno.hpengine.model.animation.AnimationController
import de.hanno.hpengine.scene.AnimatedVertex
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexStruktPacked
import de.hanno.hpengine.transform.AABB
import de.hanno.hpengine.transform.AABBData.Companion.getSurroundingAABB
import org.lwjgl.BufferUtils
import struktgen.api.TypedBuffer
import struktgen.api.forIndex
import java.io.File
import java.nio.ByteBuffer


fun AnimatedModel.putVerticesPacked(buffer: TypedBuffer<AnimatedVertexStruktPacked>, baseIndex: Int) = buffer.run {
    byteBuffer.run {
        indexedVertices.forEachIndexed { index, vertex ->
            buffer.forIndex(baseIndex + index) {
                it.position.set(vertex.position)
                it.texCoord.set(vertex.texCoord)
                it.normal.set(vertex.normal)
                it.weights.set(vertex.weights)
                it.jointIndices.set(vertex.jointIndices)
            }
        }
    }
}
fun AnimatedModel.putUnindexedVerticesPacked(buffer: TypedBuffer<AnimatedVertexStruktPacked>, baseIndex: Int) = buffer.run {
    byteBuffer.run {
        unindexedVertices.forEachIndexed { index, vertex ->
            buffer.forIndex(baseIndex + index) {
                it.position.set(vertex.position)
                it.texCoord.set(vertex.texCoord)
                it.normal.set(vertex.normal)
                when (index % 3) {
                    0 -> it.dummy.x = 1f
                    1 -> it.dummy.y = 1f
                    2 -> it.dummy.z = 1f
                }
                it.weights.set(vertex.weights)
                it.jointIndices.set(vertex.jointIndices)
            }
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

    val animationController = AnimationController(animations)

    fun update(deltaSeconds: Float) {
        animationController.update(deltaSeconds)
    }

    override val boundingVolume: AABB = calculateBoundingVolume()

    fun calculateBoundingVolume() = AABB(meshes.map { it.boundingVolume }.getSurroundingAABB())
}
