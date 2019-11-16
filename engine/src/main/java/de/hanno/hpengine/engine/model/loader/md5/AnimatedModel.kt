package de.hanno.hpengine.engine.model.loader.md5

import de.hanno.hpengine.engine.model.AbstractModel
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.scene.AnimatedVertex
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import org.joml.Matrix4f

class AnimatedModel(meshes: Array<MD5Mesh>,
                    var frames: List<AnimatedFrame>,
                    private val boundInfo: MD5BoundInfo,
                    val header: MD5AnimHeader,
                    val invJointMatrices: List<Matrix4f>,
                    override val isInvertTexCoordY: Boolean) : AbstractModel<AnimatedVertex>(meshes.toList()) {

    val animationController = AnimationController(frames.size, header.frameRate.toFloat())
    init {
        for (mesh in meshes) {
            mesh.setModel(this)
        }
    }

    override val isStatic = false

    override fun getBoundingSphereRadius(mesh: Mesh<*>): Float {
        return getCurrentBoundInfo(animationController.currentFrameIndex).boundingSphereRadius
    }

    override fun getMinMax(transform: Transform<*>, mesh: Mesh<*>): AABB {
        return getCurrentBoundInfo(animationController.currentFrameIndex).getMinMaxWorld(transform)
    }

    override fun getMinMax(transform: Transform<*>): AABB {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getMinMax(mesh: Mesh<*>): AABB {
        return getCurrentBoundInfo(animationController.currentFrameIndex).minMax
    }

    fun getCurrentBoundInfo(frame: Int): MD5BoundInfo.MD5Bound {
        return boundInfo.bounds[frame]
    }

    fun update(deltaSeconds: Float) {
        animationController.update(deltaSeconds)
    }
}
