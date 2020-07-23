package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.model.StaticMesh.Companion.calculateMinMax
import de.hanno.hpengine.engine.model.animation.Animation
import de.hanno.hpengine.engine.model.animation.AnimationController
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.AnimatedVertex
import de.hanno.hpengine.engine.scene.AnimatedVertexStruct
import de.hanno.hpengine.engine.scene.AnimatedVertexStructPacked
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.AABBData
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.struct.StructArray
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.File

class AnimatedMesh(override var name: String,
                   override val vertices: List<AnimatedVertex>,
                   override val faces: List<IndexedFace>,
                   override var material: Material): Mesh<AnimatedVertex> {
    internal var centerTemp = Vector3f()
    var model: AnimatedModel? = null

    override val indexBufferValues = StructArray(faces.size*3) { IntStruct() }.apply {
        faces.forEachIndexed { index, face ->
            this.getAtIndex(3*index).value = face.a
            this.getAtIndex(3*index+1).value = face.b
            this.getAtIndex(3*index+2).value = face.c
        }
    }

    override val triangleCount: Int
        get() = faces.size


    override val spatial: SimpleSpatial = SimpleSpatial(AABB(Vector3f(), Vector3f())).apply {
        val minLocal = Vector3f()
        val maxLocal = Vector3f()
        calculateMinMax(Mesh.IDENTITY, minLocal, maxLocal, vertices, faces)
        minMax.setLocalAABB(minLocal, maxLocal)
    }

    companion object {
        fun calculateMinMax(modelMatrix: Matrix4f?, min: Vector3f, max: Vector3f,
                            vertices: List<AnimatedVertex>, faces: Collection<IndexedFace>) {
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
    }
}

class AnimatedModel(override val file: File, meshes: List<AnimatedMesh>,
                    val animations: Map<String, Animation>, material: Material = meshes.first().material): AbstractModel<AnimatedVertex>(meshes, material) {
    override val bytesPerVertex = AnimatedVertexStruct.sizeInBytes
    override val path = file.absolutePath

    init {
        for (mesh in meshes) {
            mesh.model = this
        }
    }
    val animation = animations.entries.first().value // TOOD: Use all animations
    val animationController = AnimationController(animation)

    override val verticesStructArray = StructArray(uniqueVertices.size) { AnimatedVertexStruct() }.apply {
        for (i in uniqueVertices.indices) {
            val animatedVertex = uniqueVertices[i]
            val (position, texCoord, normal, weights, jointIndices) = animatedVertex
            val target = getAtIndex(i)
            target.position.set(position)
            target.texCoord.set(texCoord)
            target.normal.set(normal)
            target.weights.set(weights)
            target.jointIndices.set(jointIndices)
        }
    }
    override val verticesStructArrayPacked = StructArray(meshes.sumBy { it.vertices.size }) { AnimatedVertexStructPacked() }.apply {
        var counter = 0
        for (mesh in meshes) {
            for(animatedVertex in mesh.vertices) {
                val (position, texCoord, normal, weights, jointIndices) = animatedVertex
                val target = getAtIndex(counter)
                target.position.set(position)
                target.texCoord.set(texCoord)
                target.normal.set(normal)
                target.weights.set(weights)
                target.jointIndices.set(jointIndices)
                counter++
            }
        }
    }
    override val isStatic = false

    fun update(deltaSeconds: Float) {
        animationController.update(deltaSeconds)
    }

    override fun getMinMax(transform: Matrix4f, mesh: Mesh<*>): AABB {
        return minMax
    }
    override val minMax: AABB = run {
        var targetMinMax = AABBData()
        for (i in meshes.indices) {
            val mesh = meshes[i]
            val meshMinMax = mesh.spatial.minMax
            val newMin = Vector3f(meshMinMax.min)
            val newMax = Vector3f(meshMinMax.max)
            calculateMinMax(newMin, newMax, targetMinMax)
            targetMinMax = AABBData(newMin, newMax)
        }
        AABB(targetMinMax.min, targetMinMax.max)
    }
}