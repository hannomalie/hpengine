package de.hanno.hpengine.engine.transform

import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.model.StaticMesh
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.util.isEqualTo
import kotlinx.coroutines.CoroutineScope
import org.joml.Matrix4f
import org.joml.Vector3f
import java.io.Serializable

abstract class AbstractSpatial : Serializable, Spatial {
    override val centerWorld = Vector3f()
    override val minMaxWorld = AABB(Vector3f(Spatial.MIN),Vector3f(Spatial.MAX))

    override var boundingSphereRadius = -1f
        protected set

    private var lastUsedTransformationMatrix: Matrix4f? = null

    override fun getCenterWorld(transform: Transform<*>): Vector3f {
        recalculateIfNotClean(transform)
        return centerWorld
    }
    protected open fun isClean(transform: Transform<*>): Boolean {
        if(lastUsedTransformationMatrix == null) return false
        return transform.isEqualTo(lastUsedTransformationMatrix!!)
    }

    private fun calculateCenters() {
        calculateCenter(centerWorld, minMaxWorld)
    }

    fun calculateCenter(target: Vector3f, minMax: AABB) {
        target.x = minMax.min.x + (minMax.max.x - minMax.min.x) / 2
        target.y = minMax.min.y + (minMax.max.y - minMax.min.y) / 2
        target.z = minMax.min.z + (minMax.max.z - minMax.min.z) / 2
    }

    override fun getMinMaxWorld(transform: Transform<*>): AABB {
        recalculateIfNotClean(transform)
        return minMaxWorld
    }

    protected fun recalculate(transform: Transform<*>) {
        minMax.transform(transform, minMaxWorld)
        calculateBoundSphereRadius()
        calculateCenters()
        setLastUsedTransformationMatrix(transform)
    }

    private val boundingSphereTemp = Vector3f()
    fun calculateBoundSphereRadius() {
        boundingSphereRadius = StaticMesh.getBoundingSphereRadius(boundingSphereTemp, minMaxWorld.min, minMaxWorld.max)
    }

    override fun getBoundingSphereRadius(transform: Transform<*>): Float {
        recalculateIfNotClean(transform)
        return boundingSphereRadius
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

}

open class SimpleSpatial(override val minMax: AABB = AABB(Vector3f(-5f, -5f, -5f),Vector3f(5f, 5f, 5f))) : AbstractSpatial()

class TransformSpatial(val transform: Transform<*>, override val minMax: AABB = AABB(Vector3f(-5f, -5f, -5f),Vector3f(5f, 5f, 5f))) : SimpleSpatial() {
    override fun CoroutineScope.update(deltaSeconds: Float) = recalculateIfNotClean(transform)
}
open class StaticTransformSpatial(val transform: Transform<*>, val modelComponent: ModelComponent) : SimpleSpatial() {
    override val minMax
        get() = modelComponent.minMax

    override fun CoroutineScope.update(deltaSeconds: Float) = recalculateIfNotClean(transform)
}
open class AnimatedTransformSpatial(transform: Transform<*>, modelComponent: ModelComponent) : StaticTransformSpatial(transform, modelComponent) {
    override fun CoroutineScope.update(deltaSeconds: Float) = recalculate(transform)
}
