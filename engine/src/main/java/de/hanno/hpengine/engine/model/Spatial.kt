package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.Transform
import de.hanno.hpengine.util.Util
import org.joml.Matrix4f
import org.joml.Vector3f
import java.io.Serializable

private val min = Vector3f(java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE)
private val max = Vector3f(-java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE)

abstract class AbstractSpatial : Serializable, Spatial {
    @Transient protected val centerWorld = Vector3f()
    abstract protected val minMax : Array<Vector3f>
    @Transient protected val minMaxWorld = arrayOf(Vector3f(min),Vector3f(max))

    @Transient var boundingSphereRadius = -1f
    @Transient private var lastUsedTransformationMatrix: Matrix4f? = null

    override fun getCenterWorld(transform: Transform<*>): Vector3f {
        if (!isClean(transform)) {
            recalculate(transform)
        }
        return centerWorld
    }

    protected fun isClean(transform: Transform<*>): Boolean {
        return lastUsedTransformationMatrix != null && Util.equals(transform, lastUsedTransformationMatrix)
    }

    private fun calculateCenterWorld() {
        val minWorld = minMaxWorld[0]
        val maxWorld = minMaxWorld[1]

        centerWorld.x = minWorld.x + (maxWorld.x - minWorld.x) / 2
        centerWorld.y = minWorld.y + (maxWorld.y - minWorld.y) / 2
        centerWorld.z = minWorld.z + (maxWorld.z - minWorld.z) / 2
    }

    override fun getMinMaxWorld(transform: Transform<*>): Array<Vector3f> {
        if (!isClean(transform)) {
            recalculate(transform)
        }
        return minMaxWorld
    }

    protected fun recalculate(transform: Transform<*>) {
        transform.transformPosition(minMax[0], minMaxWorld[0])
        transform.transformPosition(minMax[1], minMaxWorld[1])
        boundingSphereRadius = StaticMesh.getBoundingSphereRadius(minMaxWorld[0], minMaxWorld[1])
        calculateCenterWorld()
        setLastUsedTransformationMatrix(Matrix4f(transform))
    }

    override fun getBoundingSphereRadius(transform: Transform<*>): Float {
        if (!isClean(transform)) {
            recalculate(transform)
        }
        return boundingSphereRadius
    }

    fun setLastUsedTransformationMatrix(lastUsedTransformationMatrix: Matrix4f) {
        this.lastUsedTransformationMatrix?.let { this.lastUsedTransformationMatrix = Matrix4f(lastUsedTransformationMatrix)  }
        this.lastUsedTransformationMatrix?.set(lastUsedTransformationMatrix)
    }
}

open class SimpleSpatial : AbstractSpatial() {
    override val minMax = arrayOf(Vector3f(-5f, -5f, -5f),Vector3f(5f, 5f, 5f))
}