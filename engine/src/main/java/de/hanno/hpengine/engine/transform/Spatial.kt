package de.hanno.hpengine.engine.transform

import de.hanno.hpengine.engine.model.StaticMesh
import de.hanno.hpengine.util.Util
import org.joml.Matrix4f
import org.joml.Vector3f
import java.io.Serializable

val min = Vector3f(java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE)
val max = Vector3f(-java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE)

abstract class AbstractSpatial : Serializable, Spatial {
    val centerWorld = Vector3f()
    protected val centerProperty = Vector3f()
    abstract protected val minMaxProperty : Array<Vector3f>
    open val minMaxWorldProperty = arrayOf(Vector3f(min),Vector3f(max))

    protected var boundingSphereRadiusProperty = -1f
    @Transient private var lastUsedTransformationMatrix: Matrix4f? = null

    override fun getCenterWorld(transform: Transform<*>): Vector3f {
        if (!isClean(transform)) {
            recalculate(transform)
        }
        return centerWorld
    }
    override fun getCenter() = centerProperty

    override fun getMinMaxWorld(): Array<Vector3f> = minMaxWorldProperty

    protected fun isClean(transform: Transform<*>): Boolean {
        return /*transform == null || */(lastUsedTransformationMatrix != null && Util.equals(transform, lastUsedTransformationMatrix))
    }

    private fun calculateCenters() {
        calculateCenter(centerWorld, minMaxWorldProperty)
        calculateCenter(center, minMax)
    }

    fun calculateCenter(target: Vector3f, minMax: Array<Vector3f>) {
        target.x = minMax[0].x + (minMax[1].x - minMax[0].x) / 2
        target.y = minMax[0].y + (minMax[1].y - minMax[0].y) / 2
        target.z = minMax[0].z + (minMax[1].z - minMax[0].z) / 2
    }

    override fun getMinMaxWorld(transform: Transform<*>): Array<Vector3f> {
        if (!isClean(transform)) {
            recalculate(transform)
        }
        return minMaxWorldProperty
    }

    protected fun recalculate(transform: Transform<*>) {
        transform.transformPosition(minMax[0], minMaxWorldProperty[0])
        transform.transformPosition(minMax[1], minMaxWorldProperty[1])
        calculateBoundSphereRadius()
        calculateCenters()
        setLastUsedTransformationMatrix(transform)
    }

    private val boundingSphereTemp = Vector3f()
    fun calculateBoundSphereRadius() {
        boundingSphereRadiusProperty = StaticMesh.getBoundingSphereRadius(boundingSphereTemp, minMaxWorldProperty[0], minMaxWorldProperty[1])
    }

    override fun getBoundingSphereRadius(transform: Transform<*>): Float {
        if (!isClean(transform)) {
            recalculate(transform)
        }
        return boundingSphereRadiusProperty
    }

    fun setLastUsedTransformationMatrix(lastUsedTransformationMatrix: Matrix4f) {
        this.lastUsedTransformationMatrix?.let { this.lastUsedTransformationMatrix = Matrix4f(lastUsedTransformationMatrix)  }
        this.lastUsedTransformationMatrix?.set(lastUsedTransformationMatrix)
    }

    override fun getMinMax() = minMaxProperty

    override fun getBoundingSphereRadius() = boundingSphereRadiusProperty
}

open class SimpleSpatial : AbstractSpatial() {
    override val minMaxProperty = arrayOf(Vector3f(-5f, -5f, -5f),Vector3f(5f, 5f, 5f))
}
