package de.hanno.hpengine.engine.transform

import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.model.StaticMesh
import de.hanno.hpengine.util.Util
import org.joml.Matrix4f
import org.joml.Vector3f
import java.io.Serializable

abstract class AbstractSpatial : Serializable, Spatial {
    protected val centerWorldProperty = Vector3f()

    protected abstract val minMaxProperty : AABB
    open val minMaxWorldProperty = AABB(Vector3f(Spatial.MIN),Vector3f(Spatial.MAX))

    protected var boundingSphereRadiusProperty = -1f
    @Transient private var lastUsedTransformationMatrix: Matrix4f? = null

    override fun getCenterWorld(transform: Transform<*>): Vector3f {
        recalculateIfNotClean(transform)
        return centerWorld
    }
    override fun getCenterWorld() = centerWorldProperty

    override fun getMinMaxWorld(): AABB = minMaxWorldProperty

    protected open fun isClean(transform: Transform<*>): Boolean {
        return Util.equals(transform, lastUsedTransformationMatrix)
    }

    private fun calculateCenters() {
        calculateCenter(centerWorld, minMaxWorldProperty)
    }

    fun calculateCenter(target: Vector3f, minMax: AABB) {
        target.x = minMax.min.x + (minMax.max.x - minMax.min.x) / 2
        target.y = minMax.min.y + (minMax.max.y - minMax.min.y) / 2
        target.z = minMax.min.z + (minMax.max.z - minMax.min.z) / 2
    }

    override fun getMinMaxWorld(transform: Transform<*>): AABB {
        recalculateIfNotClean(transform)
        return minMaxWorldProperty
    }

    protected fun recalculate(transform: Transform<*>) {
        minMax.transform(transform, minMaxWorldProperty)
        calculateBoundSphereRadius()
        calculateCenters()
        setLastUsedTransformationMatrix(transform)
    }

    private val boundingSphereTemp = Vector3f()
    fun calculateBoundSphereRadius() {
        boundingSphereRadiusProperty = StaticMesh.getBoundingSphereRadius(boundingSphereTemp, minMaxWorldProperty.min, minMaxWorldProperty.max)
    }

    override fun getBoundingSphereRadius(transform: Transform<*>): Float {
        recalculateIfNotClean(transform)
        return boundingSphereRadiusProperty
    }

    protected fun recalculateIfNotClean(transform: Transform<*>) {
        if (!isClean(transform)) {
            recalculate(transform)
        }
    }

    fun setLastUsedTransformationMatrix(lastUsedTransformationMatrix: Matrix4f) {
        if(this.lastUsedTransformationMatrix == null) {
            this.lastUsedTransformationMatrix = Matrix4f(lastUsedTransformationMatrix)
        }
        this.lastUsedTransformationMatrix?.set(lastUsedTransformationMatrix)
    }

    override fun getMinMax() = minMaxProperty

    override fun getBoundingSphereRadius() = boundingSphereRadiusProperty

}

open class SimpleSpatial(override val minMaxProperty: AABB = AABB(Vector3f(-5f, -5f, -5f),Vector3f(5f, 5f, 5f))) : AbstractSpatial()

open class TransformSpatial(val transform: Transform<*>, override val minMaxProperty: AABB = AABB(Vector3f(-5f, -5f, -5f),Vector3f(5f, 5f, 5f))) : SimpleSpatial() {
    override fun update(deltaSeconds: Float) {
        super.recalculateIfNotClean(transform)
    }
}
open class StaticTransformSpatial(val transform: Transform<*>, val modelComponent: ModelComponent) : SimpleSpatial() {

    override fun getMinMax(): AABB {
        return modelComponent.minMax
    }

    override fun update(deltaSeconds: Float) {
        super.recalculateIfNotClean(transform)
    }
}
open class AnimatedTransformSpatial(transform: Transform<*>, modelComponent: ModelComponent) : StaticTransformSpatial(transform, modelComponent) {
    override fun update(deltaSeconds: Float) {
        recalculate(transform)
    }
}
