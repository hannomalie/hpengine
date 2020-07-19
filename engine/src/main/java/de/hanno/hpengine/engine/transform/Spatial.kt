package de.hanno.hpengine.engine.transform

import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.model.StaticMesh
import de.hanno.hpengine.util.isEqualTo
import kotlinx.coroutines.CoroutineScope
import org.joml.Matrix4f
import org.joml.Vector3f
import java.io.Serializable

open class SimpleSpatial(val minMaxLocal: AABB = AABB(Vector3f(Spatial.MIN),Vector3f(Spatial.MAX))) : Serializable, Spatial {

    val centerLocal = Vector3f().apply {
        calculateCenter(this, minMaxLocal)
    }
    private val centerWorld = Vector3f()
    private val minMaxWorld = AABB()

    inline val minLocal: Vector3f
        get() = minMaxLocal.min

    inline val maxLocal: Vector3f
        get() = minMaxLocal.max

    override var boundingSphereRadius = -1f
        protected set

    private var lastUsedTransformationMatrix: Matrix4f? = null

    override fun getCenter(transform: Transform<*>): Vector3f {
        recalculateIfNotClean(transform)
        return centerWorld
    }

    fun isClean(transform: Transform<*>): Boolean {
        if(lastUsedTransformationMatrix == null) return false
        return transform.isEqualTo(lastUsedTransformationMatrix!!)
    }

    fun calculateCenter(target: Vector3f = centerLocal, minMax: AABB = minMaxLocal) {
        target.x = minMax.min.x + (minMax.max.x - minMax.min.x) / 2
        target.y = minMax.min.y + (minMax.max.y - minMax.min.y) / 2
        target.z = minMax.min.z + (minMax.max.z - minMax.min.z) / 2
    }

    override fun getMinMax(transform: Transform<*>): AABB {
        recalculateIfNotClean(transform)
        return minMaxWorld
    }

    protected open fun recalculate(transform: Transform<*>) {
        minMaxLocal.transform(transform, minMaxWorld)
        calculateBoundSphereRadius()
        calculateCenter(centerWorld, minMaxWorld)
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

open class TransformSpatial(val transform: Transform<*>, minMaxLocal: AABB) : SimpleSpatial(minMaxLocal) {
    override fun CoroutineScope.update(deltaSeconds: Float) = recalculateIfNotClean(transform)

    val minMax: AABB
        get() = super.getMinMax(transform)
    val center: Vector3f
        get() = super.getCenter(transform)

}
open class StaticTransformSpatial(transform: Transform<*>, val modelComponent: ModelComponent) : TransformSpatial(transform, modelComponent.minMax) {
    override fun CoroutineScope.update(deltaSeconds: Float) = recalculateIfNotClean(transform)
}
open class AnimatedTransformSpatial(transform: Transform<*>, modelComponent: ModelComponent) : StaticTransformSpatial(transform, modelComponent) {
    override fun CoroutineScope.update(deltaSeconds: Float) = recalculate(transform)
}
