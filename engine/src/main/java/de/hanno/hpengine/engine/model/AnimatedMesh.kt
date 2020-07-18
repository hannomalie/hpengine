package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.model.animation.Animation
import de.hanno.hpengine.engine.model.animation.AnimationController
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.AnimatedVertex
import de.hanno.hpengine.engine.scene.AnimatedVertexStruct
import de.hanno.hpengine.engine.scene.AnimatedVertexStructPacked
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.struct.StructArray
import org.joml.Vector3f
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


    override val boundingSphereRadius: Float
        get() = model!!.boundingSphereRadius

    override fun getMinMax(transform: Transform<*>): AABB {
        return model!!.getMinMax(transform)
    }

    override fun getCenter(entity: Entity): Vector3f {
        val component = entity.getComponent(ModelComponent::class.java)
        val animatedModel = component!!.model as AnimatedModel
        return entity.transformPosition(centerTemp)
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

//    override fun getBoundingSphereRadius(mesh: Mesh<*>): Float {
//        return getCurrentBoundInfo(animationController.currentFrameIndex).boundingSphereRadius
//    }
//
//    override fun getMinMax(transform: Transform<*>, mesh: Mesh<*>): AABB {
//        return getCurrentBoundInfo(animationController.currentFrameIndex).getMinMaxWorld(transform)
//    }
//
//    override fun getMinMax(transform: Transform<*>): AABB {
//        return getCurrentBoundInfo(animationController.currentFrameIndex).minMax
//    }
//
//    override fun getMinMax(mesh: Mesh<*>): AABB {
//        return getCurrentBoundInfo(animationController.currentFrameIndex).minMax
//    }
//
//    fun getCurrentBoundInfo(frame: Int): MD5BoundInfo.MD5Bound {
//        return animation.frames[frame].bounds[frame]
//    }

    fun update(deltaSeconds: Float) {
        animationController.update(deltaSeconds)
    }

    // TODO: Implement this properly
    val aabb = AABB(Vector3f(-1000f), Vector3f(1000f))
    override fun getMinMax(transform: Transform<*>): AABB {
        return aabb
    }
}