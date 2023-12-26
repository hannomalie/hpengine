package de.hanno.hpengine.transform

import de.hanno.hpengine.Transform
import de.hanno.hpengine.Transform.Companion.IDENTITY
import de.hanno.hpengine.camera.Frustum
import de.hanno.hpengine.model.CompiledFace
import de.hanno.hpengine.model.IndexedFace
import de.hanno.hpengine.model.Instance
import org.joml.*

sealed class BoundingVolume

data class BoundingSphereTrafo(val translation: Vector3f = Vector3f(), var scale: Float = 1f)
class BoundingSphere(val positionRadius: Vector4fc): BoundingVolume() {
    private var lastUsedTransformationMatrix: Matrix4f? = null
    private var lastUsedBoundingSphereTrafo: BoundingSphereTrafo? = null

    var localBoundingSphere = Vector4f(0f, 0f, 0f, 1f) // TODO: Is this a good default?
        set(value) {
            field = value
            actuallyRecalculate(lastUsedTransformationMatrix ?: IDENTITY)
            lastUsedTransformationMatrix = null
        }
    var boundingSphere = Vector4f(0f, 0f, 0f, 1f) // TODO: Is this a good default?
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
    private val _temp = Matrix4f()
    private fun actuallyRecalculate(transform: Matrix4f) {

        _temp.set(transform)
        val transform = _temp

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
        val positionRadius = Vector4f(localBoundingSphere).add(translation)
        positionRadius.w = localBoundingSphere.w * lastUsedBoundingSphereTrafo!!.scale
        boundingSphere = positionRadius
    }

    private fun recalculateIfNotClean(transform: Matrix4f) {
        if (!isClean(transform)) {
            actuallyRecalculate(transform)
        }
    }
}

data class AABBData(val min: Vector3f = Vector3f(absoluteMaximum), val max: Vector3f = Vector3f(absoluteMinimum)) {
    val extents = Vector3f()
    val halfExtents = Vector3f()
    val center = Vector3f()

    var boundingSphereRadius = 0.0f
        private set

    private fun updateExtents() { extents.set(max).sub(min) }
    private fun updateHalfExtents() { halfExtents.set(extents).mul(0.5f) }
    private fun updateCenter() { center.set(min).add(halfExtents) }
    private fun updateBoundingSphereRadius() { boundingSphereRadius = halfExtents[halfExtents.maxComponent()] }
    fun update() {
        updateExtents()
        updateHalfExtents()
        updateCenter()
        updateBoundingSphereRadius()
    }

    init {
        update()
    }

    inline fun recaclulating(block: AABBData.() -> Unit) {
        block()
        update()
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
            return AABBData(newMin, newMax)
        }
        fun List<AABB>.getSurroundingAABB(): AABBData {
            val newMin = Vector3f(first().min)
            val newMax = Vector3f(first().max)
            forEach {
                newMin.min(it.min)
                newMax.max(it.max)
            }
            return AABBData(newMin, newMax)
        }

        @JvmName("getSurroundingAABBForInstances")
        fun List<Instance>.getSurroundingAABB(): AABBData {
            val newMin = Vector3f(first().boundingVolume.min)
            val newMax = Vector3f(first().boundingVolume.max)
            forEach {
                newMin.min(it.boundingVolume.min)
                newMax.max(it.boundingVolume.max)
            }
            return AABBData(newMin, newMax)
        }
    }
}

class AABB(localMin: Vector3f = Vector3f(absoluteMaximum), localMax: Vector3f = Vector3f(absoluteMinimum)): BoundingVolume() {
    constructor(aabbData: AABBData): this(aabbData.min, aabbData.max)

    val localAABB = AABBData(localMin, localMax)

    val worldAABB = AABBData()

    inline val min: Vector3f get() = worldAABB.min

    inline val max: Vector3f get() = worldAABB.max

    var localMin: Vector3f
        get() = localAABB.min
        set(value) = recalculating {
            localAABB.min.set(value)
        }

    var localMax: Vector3f
        get() = localAABB.max
        set(value) = recalculating {
            localAABB.max.set(value)
        }

    fun recalculating(block: AABB.() -> Unit) {
        block()
        actuallyRecalculate(lastUsedTransformationMatrix ?: IDENTITY)
        lastUsedTransformationMatrix = null
    }

    inline val extents: Vector3f get() = worldAABB.extents
    inline val halfExtents: Vector3f get() = worldAABB.halfExtents

    inline val boundingSphereRadius: Float get() = worldAABB.boundingSphereRadius

    inline val center: Vector3f get() = worldAABB.center

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
            actuallyRecalculate(transform)
        }
    }
    constructor(center: Vector3f, halfExtents: Float):
            this(Vector3f(center).sub(Vector3f(halfExtents)), Vector3f(center).add(Vector3f(halfExtents)))

    private fun transform(transform: Matrix4f) {
        transform.transformPosition(localAABB.min, corners[0])
        transform.transformPosition(corners[1].set(localAABB.min.x, localAABB.min.y, localAABB.max.z), corners[1])
        transform.transformPosition(corners[2].set(localAABB.max.x, localAABB.min.y, localAABB.min.z), corners[2])
        transform.transformPosition(corners[3].set(localAABB.max.x, localAABB.min.y, localAABB.max.z), corners[3])

        transform.transformPosition(localAABB.max, corners[4])
        transform.transformPosition(corners[5].set(localAABB.min.x, localAABB.max.y, localAABB.max.z), corners[5])
        transform.transformPosition(corners[6].set(localAABB.max.x, localAABB.max.y, localAABB.min.z), corners[6])
        transform.transformPosition(corners[7].set(localAABB.min.x, localAABB.max.y, localAABB.min.z), corners[7])

        val minResult = Vector3f(absoluteMaximum)
        val maxResult = Vector3f(absoluteMinimum)
        corners.forEach { corner ->
            minResult.min(corner)
            maxResult.max(corner)
        }

        worldAABB.recaclulating {
            min.set(minResult)
            max.set(maxResult)
        }
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

fun calculateAABB(
    modelMatrix: Matrix4f?,
    positions: List<Vector3fc>,
    faces: Collection<IndexedFace>
): AABBData {
    val min = Vector3f(absoluteMaximum)
    val max = Vector3f(absoluteMinimum)

    for (face in faces) {
        val vertices = listOf(positions[face.a], positions[face.b], positions[face.c])

        for (j in 0..2) {
            val positionV3 = vertices[j]
            val position = Vector4f(positionV3.x(), positionV3.y(), positionV3.z(), 1f)
            if (modelMatrix != null) {
                position.mul(modelMatrix)
            }

            min.x = if (position.x < min.x) position.x else min.x
            min.y = if (position.y < min.y) position.y else min.y
            min.z = if (position.z < min.z) position.z else min.z

            max.x = if (position.x > max.x) position.x else max.x
            max.y = if (position.y > max.y) position.y else max.y
            max.z = if (position.z > max.z) position.z else max.z
        }
    }

    return AABBData(Vector3f(min), Vector3f(max))
}

fun calculateAABB(modelMatrix: Matrix4f?, min: Vector3f, max: Vector3f, faces: List<CompiledFace>) {
    min.set(java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE)
    max.set(-java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE)

    for (i in faces.indices) {
        val face = faces[i]

        val vertices = face.vertices

        for (j in 0..2) {
            val positionV3 = vertices[j].position
            val position = Vector4f(positionV3.x, positionV3.y, positionV3.z, 1f)
            if (modelMatrix != null) {
                position.mul(modelMatrix)
            }

            min.x = if (position.x < min.x) position.x else min.x
            min.y = if (position.y < min.y) position.y else min.y
            min.z = if (position.z < min.z) position.z else min.z

            max.x = if (position.x > max.x) position.x else max.x
            max.y = if (position.y > max.y) position.y else max.y
            max.z = if (position.z > max.z) position.z else max.z
        }
    }

}

fun calculateAABB(currentMin: Vector3fc, currentMax: Vector3fc, candidate: AABBData): AABBData {
    val newMin = Vector3f(currentMin).min(candidate.min)
    val newMax = Vector3f(currentMax).max(candidate.max)
    return AABBData(newMin, newMax)
}

fun calculateMin(old: Vector3f, candidate: Vector3f) {
    old.x = if (candidate.x < old.x) candidate.x else old.x
    old.y = if (candidate.y < old.y) candidate.y else old.y
    old.z = if (candidate.z < old.z) candidate.z else old.z
}

fun calculateMax(old: Vector3f, candidate: Vector3f) {
    old.x = if (candidate.x > old.x) candidate.x else old.x
    old.y = if (candidate.y > old.y) candidate.y else old.y
    old.z = if (candidate.z > old.z) candidate.z else old.z
}

fun getBoundingSphereRadius(target: Vector3f, min: Vector3f, max: Vector3f) = target.set(max).sub(min).mul(0.5f).length()

fun getBoundingSphereRadius(target: Vector3f, min: Vector4f, max: Vector4f) =
    getBoundingSphereRadius(target, Vector3f(min.x, min.y, min.z), Vector3f(max.x, max.y, max.z))

val absoluteMaximum = Vector3f(Float.MAX_VALUE).toImmutable()
val absoluteMinimum = Vector3f(-Float.MAX_VALUE).toImmutable()


fun AABBData.setFrom(source: AABBData) {
    recaclulating {
        min.set(source.min)
        max.set(source.max)
    }
}