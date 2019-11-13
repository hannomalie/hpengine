package de.hanno.hpengine.engine.model.loader.md5

import com.carrotsearch.hppc.FloatArrayList
import com.carrotsearch.hppc.IntArrayList
import de.hanno.hpengine.engine.model.AbstractModel
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.scene.AnimatedVertex
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.ArrayList
import java.util.Arrays

class AnimatedModel(meshes: Array<MD5Mesh>,
                    var frames: List<AnimatedFrame>,
                    private val boundInfo: MD5BoundInfo,
                    val header: MD5AnimHeader,
                    val invJointMatrices: List<Matrix4f>,
                    private val invertTexCoordy: Boolean) : AbstractModel<AnimatedVertex>(listOf<Mesh<AnimatedVertex>>(*meshes)) {

    val animationController = AnimationController(frames.size, header.frameRate.toFloat())
    init {
        for (mesh in meshes) {
            mesh.setModel(this)
        }
    }


    override fun setMaterial(material: SimpleMaterial) {
        for (mesh in meshes) {
            mesh.material = material
        }
    }

    override fun getMeshes(): List<Mesh<AnimatedVertex>> {
        return meshes
    }

    override fun getTriangleCount(): Int {
        return 0
    }

    override fun getVertexBufferValuesArray(): FloatArray {
        val floatList = FloatArrayList()
        for (mesh in getMeshes()) {
            floatList.add(*mesh.vertexBufferValuesArray)
        }
        return floatList.toArray()
    }

    override fun getIndices(): IntArray {
        val intList = IntArrayList()
        var currentIndexOffset = 0
        for (mesh in getMeshes()) {
            val indexBufferValuesArray = Arrays.copyOf(mesh.indexBufferValuesArray, mesh.indexBufferValuesArray.size)
            for (i in indexBufferValuesArray.indices) {
                indexBufferValuesArray[i] += currentIndexOffset
            }
            val vertexCount = mesh.vertexBufferValuesArray.size / MD5Mesh.VALUES_PER_VERTEX
            currentIndexOffset += vertexCount
            intList.add(*indexBufferValuesArray)
        }
        return intList.toArray()
    }

    override fun getMeshIndices(): Array<IntArrayList> {
        val list = getMeshes().map { mesh -> mesh.indexBufferValues }
        return list.toTypedArray()
    }

    override fun getCompiledVertices(): List<AnimatedVertex> {
        val vertexList = ArrayList<AnimatedVertex>()
        for (mesh in getMeshes()) {
            vertexList.addAll(mesh.compiledVertices)
        }
        return vertexList
    }

    override fun isStatic(): Boolean {
        return false
    }

    override fun getBoundingSphereRadius(mesh: Mesh<*>): Float {
        return getCurrentBoundInfo(animationController.currentFrameIndex).boundingSphereRadius
    }

    override fun getMinMax(transform: Transform<*>, mesh: Mesh<*>): AABB {
        return getCurrentBoundInfo(animationController.currentFrameIndex).getMinMaxWorld(transform)
    }

    override fun getMinMax(mesh: Mesh<*>): AABB {
        return getCurrentBoundInfo(animationController.currentFrameIndex).minMax
    }

    fun getCurrentBoundInfo(frame: Int): MD5BoundInfo.MD5Bound {
        return boundInfo.bounds[frame]
    }

    override fun getCenterWorld(transform: Transform<*>): Vector3f {
        return super.getCenterWorld(transform)
    }

    override fun getMinMaxWorld(transform: Transform<*>): AABB {
        return super.getMinMaxWorld(transform)
    }

    override fun getBoundingSphereRadius(transform: Transform<*>): Float {
        return super.getBoundingSphereRadius(transform)
    }

    override fun isInvertTexCoordY(): Boolean {
        return invertTexCoordy
    }

    fun update(deltaSeconds: Float) {
        animationController.update(deltaSeconds)
    }
}
