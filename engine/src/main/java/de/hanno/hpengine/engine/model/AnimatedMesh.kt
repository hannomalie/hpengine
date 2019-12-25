package de.hanno.hpengine.engine.model

import com.carrotsearch.hppc.FloatArrayList
import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.AnimatedVertex
import de.hanno.hpengine.engine.scene.AnimatedVertexStruct
import de.hanno.hpengine.engine.scene.AnimatedVertexStructPacked
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.struct.StructArray
import org.joml.Vector3f

class AnimatedMesh(override var name: String,
                   override val uniqueVertices: List<AnimatedVertex>,
                   val faces: List<IndexedFace>,
                   override var material: Material): Mesh<AnimatedVertex> {
    internal var centerTemp = Vector3f()
    var model: AnimatedModel? = null

    override val indexBufferValues = IntArrayList().apply {
        faces.forEach { face ->
            add(face.a)
            add(face.b)
            add(face.c)
        }
    }.toArray()

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

class AnimatedModel(override val path: String, meshes: List<AnimatedMesh>,
                    val animations: Map<String, Animation>, material: Material = meshes.first().material): AbstractModel<AnimatedVertex>(meshes, material) {
    override val bytesPerVertex = AnimatedVertexStruct.sizeInBytes
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
    override val verticesStructArrayPacked = StructArray(meshes.sumBy { it.uniqueVertices.size }) { AnimatedVertexStructPacked() }.apply {
        var counter = 0
        for (mesh in meshes) {
            for(animatedVertex in mesh.uniqueVertices) {
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