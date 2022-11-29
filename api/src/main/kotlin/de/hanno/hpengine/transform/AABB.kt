package de.hanno.hpengine.transform

import de.hanno.hpengine.Transform
import de.hanno.hpengine.Transform.Companion.IDENTITY
import de.hanno.hpengine.camera.Frustum
import de.hanno.hpengine.model.Instance
import org.joml.*

sealed class BoundingVolume

data class BoundingSphereTrafo(val translation: Vector3f = Vector3f(), var scale: Float = 1f) {
    companion object {
        val identity = BoundingSphereData()
    }
}
data class BoundingSphereData(val positionRadius: Vector4fc = Vector4f(0f, 0f, 0f, absoluteMinimum.x))
class BoundingSphere(val positionRadius: Vector4fc): BoundingVolume() {
    private var lastUsedTransformationMatrix: Matrix4f? = null
    private var lastUsedBoundingSphereTrafo: BoundingSphereTrafo? = null

    var localBoundingSphere = BoundingSphereData()
        set(value) {
            field = value
            actuallyRecalculate(lastUsedTransformationMatrix ?: Transform.IDENTITY)
            lastUsedTransformationMatrix = null
        }
    var boundingSphere = BoundingSphereData()
        private set

    init {
        recalculate(IDENTITY)
        lastUsedTransformationMatrix = null
    }

    private val scaleTemp = Vector3f()
    private fun isClean(transform: Matrix4f): Boolean {
        if(lastUsedTransformationMatrix == null) return false
        return transform == lastUsedTransformationMatrix!!
    }

    fun recalculate(transform: Matrix4f) {
        recalculateIfNotClean(transform)
    }
    private fun actuallyRecalculate(transform: Matrix4f) {
        lastUsedTransformationMatrix = transform
        lastUsedBoundingSphereTrafo = BoundingSphereTrafo()

        transform.getTranslation(lastUsedBoundingSphereTrafo!!.translation)
        transform.getScale(scaleTemp)
        lastUsedBoundingSphereTrafo!!.scale = scaleTemp[scaleTemp.maxComponent()]

        val translation = Vector4f(
            lastUsedBoundingSphereTrafo!!.translation.x,
            lastUsedBoundingSphereTrafo!!.translation.y,
            lastUsedBoundingSphereTrafo!!.translation.z,
            0f
        )
        val positionRadius = Vector4f(localBoundingSphere.positionRadius).add(translation)
        positionRadius.w = localBoundingSphere.positionRadius.w * lastUsedBoundingSphereTrafo!!.scale
        boundingSphere = BoundingSphereData(positionRadius)
    }

    private fun recalculateIfNotClean(transform: Matrix4f) {
        if (!isClean(transform)) {
            actuallyRecalculate(Matrix4f(transform))
        }
    }
}

data class AABBData(val min: Vector3fc = Vector3f(absoluteMaximum), val max: Vector3fc = Vector3f(absoluteMinimum)) {
    val extents by lazy {
        Vector3f(max).sub(min)
    }
    val halfExtents by lazy {
        Vector3f(extents).mul(0.5f)
    }
    val center by lazy {
        Vector3f(min).add(halfExtents)
    }
    val boundingSphereRadius by lazy {
        halfExtents.get(halfExtents.maxComponent())
    }
    companion object {
        @JvmName("getSurroundingAABBData")
        fun List<AABBData>.getSurroundingAABB(): AABBData {
            val newMin = Vector3f(first().min)
            val newMax = Vector3f(first().max)
            forEach {
                newMin.min(it.min)
                newMax.max(it.max)
            }
            return AABBData(newMin.toImmutable(), newMax.toImmutable())
        }
        fun List<AABB>.getSurroundingAABB(): AABBData {
            val newMin = Vector3f(first().min)
            val newMax = Vector3f(first().max)
            forEach {
                newMin.min(it.min)
                newMax.max(it.max)
            }
            return AABBData(newMin.toImmutable(), newMax.toImmutable())
        }

        @JvmName("getSurroundingAABBForInstances")
        fun List<Instance>.getSurroundingAABB(): AABBData {
            val newMin = Vector3f(first().boundingVolume.min)
            val newMax = Vector3f(first().boundingVolume.max)
            forEach {
                newMin.min(it.boundingVolume.min)
                newMax.max(it.boundingVolume.max)
            }
            return AABBData(newMin.toImmutable(), newMax.toImmutable())
        }
    }
}

class AABB(localMin: Vector3fc = Vector3f(absoluteMaximum), localMax: Vector3fc = Vector3f(absoluteMinimum)): BoundingVolume() {
    constructor(aabbData: AABBData): this(aabbData.min, aabbData.max)

    var localAABB = AABBData(localMin, localMax)
        set(value) {
            field = value
            actuallyRecalculate(lastUsedTransformationMatrix ?: IDENTITY)
            lastUsedTransformationMatrix = null
        }

    var worldAABB = AABBData()
        private set

    inline val min: Vector3fc
        get() = worldAABB.min

    inline val max: Vector3fc
        get() = worldAABB.max

    var localMin: Vector3fc
        get() = localAABB.min
        set(value) {
            localAABB = AABBData(value, localMax)
        }

    var localMax: Vector3fc
        get() = localAABB.max
        set(value) {
            localAABB = AABBData(localMin, value)
        }

    inline val extents: Vector3f
        get() = worldAABB.extents
    inline val halfExtents: Vector3f
        get() = worldAABB.halfExtents

    inline val boundingSphereRadius: Float
        get() = worldAABB.boundingSphereRadius

    inline val center: Vector3f
        get() = worldAABB.center

    private var lastUsedTransformationMatrix: Matrix4f? = null

    val corners = Array(8) { Vector3f() }

    init {
        recalculate(IDENTITY)
        lastUsedTransformationMatrix = null
    }

    fun isClean(transform: Matrix4f): Boolean {
        if(lastUsedTransformationMatrix == null) return false
        return transform == lastUsedTransformationMatrix!!
    }

    fun recalculate(transform: Matrix4f) {
        recalculateIfNotClean(transform)
    }
    private fun actuallyRecalculate(transform: Matrix4f) {
        transform(transform)
        lastUsedTransformationMatrix = transform
    }

    private fun recalculateIfNotClean(transform: Matrix4f) {
        if (!isClean(transform)) {
            actuallyRecalculate(Matrix4f(transform))
        }
    }
    constructor(center: Vector3f, halfExtents: Float):
            this(Vector3f(center).sub(Vector3f(halfExtents)), Vector3f(center).add(Vector3f(halfExtents)))

    private fun transform(transform: Matrix4f) {
        val localMin = Vector3f(localAABB.min)
        val localMax = Vector3f(localAABB.max)

        transform.transformPosition(localMin, corners[0])
        transform.transformPosition(corners[1].set(localMin.x, localMin.y, localMax.z), corners[1])
        transform.transformPosition(corners[2].set(localMax.x, localMin.y, localMin.z), corners[2])
        transform.transformPosition(corners[3].set(localMax.x, localMin.y, localMax.z), corners[3])

        transform.transformPosition(localMax, corners[4])
        transform.transformPosition(corners[5].set(localMin.x, localMax.y, localMax.z), corners[5])
        transform.transformPosition(corners[6].set(localMax.x, localMax.y, localMin.z), corners[6])
        transform.transformPosition(corners[7].set(localMin.x, localMax.y, localMin.z), corners[7])

        val minResult = Vector3f(absoluteMaximum)
        val maxResult = Vector3f(absoluteMinimum)
        corners.forEach { corner ->
            minResult.min(corner)
            maxResult.max(corner)
        }
        worldAABB = AABBData(minResult, maxResult)
    }

    fun contains(position: Vector3fc): Boolean = contains(Vector4f(position.x, position.y, position.z, 1.0f))
    fun contains(position: Vector4f): Boolean {
        return min.x < position.x && max.x > position.x &&
                min.y < position.y && max.y > position.y &&
                min.z < position.z && max.z > position.z
    }


    fun getPoints(): List<Vector3fc> {
        val result: MutableList<Vector3fc> = ArrayList()
        result.add(min)
        result.add(Vector3f(min.x + extents.x, min.y, min.z))
        result.add(Vector3f(min.x + extents.x, min.y, min.z + extents.z))
        result.add(Vector3f(min.x, min.y, min.z + extents.z))
        result.add(max)
        result.add(Vector3f(max.x - extents.x, max.y, max.z))
        result.add(Vector3f(max.x - extents.x, max.y, max.z - extents.z))
        result.add(Vector3f(max.x, max.y, max.z - extents.z))
        return result
    }
    fun getPointsAsArray(): FloatArray {
        val points: List<Vector3fc> = getPoints()
        val pointsForLineDrawing: MutableList<Vector3fc> = ArrayList<Vector3fc>().apply {
            add(points[0])
            add(points[1])
            add(points[1])
            add(points[2])
            add(points[2])
            add(points[3])
            add(points[3])
            add(points[0])
            add(points[4])
            add(points[5])
            add(points[5])
            add(points[6])
            add(points[6])
            add(points[7])
            add(points[7])
            add(points[4])
            add(points[0])
            add(points[6])
            add(points[1])
            add(points[7])
            add(points[2])
            add(points[4])
            add(points[3])
            add(points[5])
        }
        val dest = FloatArray(3 * pointsForLineDrawing.size)
        for (i in pointsForLineDrawing.indices) {
            dest[3 * i] = pointsForLineDrawing[i].x
            dest[3 * i + 1] = pointsForLineDrawing[i].y
            dest[3 * i + 2] = pointsForLineDrawing[i].z
        }
        return dest
    }

    fun move(amount: Vector3f) = transform(Transform().apply { setTranslation(amount) })

    operator fun component1() = min
    operator fun component2() = max

}

@JvmName("calculateAABBForAABBs")
fun List<AABB>.calculateAABB(): AABBData {
    val minResult = Vector3f(absoluteMaximum)
    val maxResult = Vector3f(absoluteMinimum)
    forEach {
        minResult.min(it.min)
        maxResult.max(it.max)
    }
    return AABBData(minResult, maxResult)
}

fun AABB.isInFrustum(frustum: Frustum): Boolean {
    val centerWorld = Vector3f()
    min.add(halfExtents, centerWorld)
    return frustum.sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, halfExtents.x.coerceAtLeast(halfExtents.y.coerceAtLeast(halfExtents.z)) / 2f)
}

// TODO: Fix
fun AABB.containsOrIntersectsSphere(position: Vector3f, radius: Float): Boolean {
    var result = false
    if (contains(Vector4f(position.x, position.y, position.z, 0f))) {
        result = true
        return result
    }
    val points: List<Vector3fc> = getPoints()
    val smallestDistance: Float = smallestDistance(points, position)
    val largestDistance: Float = largestDistance(points, position)
    if (largestDistance <= radius) {
        result = true
    }
    return result
}

private fun smallestDistance(points: List<Vector3fc>, pivot: Vector3f): Float {
    var length = Float.MAX_VALUE
    for (point in points) {
        val tempLength = Vector3f(point).sub(pivot).length()
        length = if (tempLength <= length) tempLength else length
    }
    return length
}

private fun largestDistance(points: List<Vector3fc>, pivot: Vector3f): Float {
    var length = Float.MAX_VALUE
    for (point in points) {
        val tempLength = Vector3f(point).sub(pivot).length()
        length = if (tempLength >= length) tempLength else length
    }
    return length
}

val absoluteMaximum = Vector3f(Float.MAX_VALUE).toImmutable()
val absoluteMinimum = Vector3f(-Float.MAX_VALUE).toImmutable()
