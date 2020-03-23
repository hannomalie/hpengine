package de.hanno.hpengine.engine.transform

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import java.util.ArrayList

data class AABB(var min: Vector3f = Vector3f(), var max: Vector3f = Vector3f()) {
    val extents: Vector3f
            get() = Vector3f(max).sub(min)
    val halfExtents: Vector3f
            get() = Vector3f(extents).mul(0.5f)

    constructor(center: Vector3f, halfExtents: Float):
            this(Vector3f(center).sub(Vector3f(halfExtents)), Vector3f(center).add(Vector3f(halfExtents)))

    private val  corners = Array(8) { Vector3f() }

    fun transform(transform: Transform<*>, minMaxWorldProperty: AABB) {
        transform.transformPosition(min, corners[0])
        transform.transformPosition(corners[1].set(min.x, min.y, max.z), corners[1])
        transform.transformPosition(corners[2].set(max.x, min.y, min.z), corners[2])
        transform.transformPosition(corners[3].set(max.x, min.y, max.z), corners[3])

        transform.transformPosition(max, corners[4])
        transform.transformPosition(corners[5].set(min.x, max.y, max.z), corners[5])
        transform.transformPosition(corners[6].set(max.x, max.y, min.z), corners[6])
        transform.transformPosition(corners[7].set(min.x, max.y, min.z), corners[7])

        minMaxWorldProperty.resetToAbsoluteMinMax()
        corners.forEach { corner ->
            minMaxWorldProperty.min.min(corner)
            minMaxWorldProperty.max.max(corner)
        }
    }

    private fun resetToAbsoluteMinMax() {
        min.set(Spatial.MIN)
        max.set(Spatial.MAX)
    }

    private fun clear() {
        min.set(0f,0f,0f)
        max.set(0f,0f,0f)
    }

    fun contains(position: Vector3f): Boolean = contains(Vector4f(position.x, position.y, position.z, 1.0f))
    fun contains(position: Vector4f): Boolean {
        return min.x < position.x && max.x > position.x &&
                min.y < position.y && max.y > position.y &&
                min.z < position.z && max.z > position.z
    }


    fun getPoints(): List<Vector3f> {
        val result: MutableList<Vector3f> = ArrayList()
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
    fun getPointsAsArray(): FloatArray? {
        val points: List<Vector3f> = getPoints()
        val pointsForLineDrawing: MutableList<Vector3f> = ArrayList()
        pointsForLineDrawing.add(points[0])
        pointsForLineDrawing.add(points[1])
        pointsForLineDrawing.add(points[1])
        pointsForLineDrawing.add(points[2])
        pointsForLineDrawing.add(points[2])
        pointsForLineDrawing.add(points[3])
        pointsForLineDrawing.add(points[3])
        pointsForLineDrawing.add(points[0])
        pointsForLineDrawing.add(points[4])
        pointsForLineDrawing.add(points[5])
        pointsForLineDrawing.add(points[5])
        pointsForLineDrawing.add(points[6])
        pointsForLineDrawing.add(points[6])
        pointsForLineDrawing.add(points[7])
        pointsForLineDrawing.add(points[7])
        pointsForLineDrawing.add(points[4])
        pointsForLineDrawing.add(points[0])
        pointsForLineDrawing.add(points[6])
        pointsForLineDrawing.add(points[1])
        pointsForLineDrawing.add(points[7])
        pointsForLineDrawing.add(points[2])
        pointsForLineDrawing.add(points[4])
        pointsForLineDrawing.add(points[3])
        pointsForLineDrawing.add(points[5])
        val dest = FloatArray(3 * pointsForLineDrawing.size)
        for (i in pointsForLineDrawing.indices) {
            dest[3 * i] = pointsForLineDrawing[i].x
            dest[3 * i + 1] = pointsForLineDrawing[i].y
            dest[3 * i + 2] = pointsForLineDrawing[i].z
        }
        return dest
    }

    fun move(amount: Vector3f) {
        min.add(amount)
        max.add(amount)
    }
}


fun AABB.isInFrustum(camera: Camera): Boolean {
    val centerWorld = Vector3f()
    min.add(halfExtents, centerWorld)
    return camera.frustum.sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, halfExtents.x.coerceAtLeast(halfExtents.y.coerceAtLeast(halfExtents.z)) / 2f)
}

// TODO: Fix
fun AABB.containsOrIntersectsSphere(position: Vector3f, radius: Float): Boolean {
    var result = false
    if (contains(Vector4f(position.x, position.y, position.z, 0f))) {
        result = true
        return result
    }
    val points: List<Vector3f> = getPoints()
    val smallestDistance: Float = smallestDistance(points, position)
    val largestDistance: Float = largestDistance(points, position)
    if (largestDistance <= radius) {
        result = true
    }
    return result
}

private fun smallestDistance(points: List<Vector3f>, pivot: Vector3f): Float {
    var length = Float.MAX_VALUE
    for (point in points) {
        val tempLength = Vector3f(point).sub(pivot).length()
        length = if (tempLength <= length) tempLength else length
    }
    return length
}

private fun largestDistance(points: List<Vector3f>, pivot: Vector3f): Float {
    var length = Float.MAX_VALUE
    for (point in points) {
        val tempLength = Vector3f(point).sub(pivot).length()
        length = if (tempLength >= length) tempLength else length
    }
    return length
}

private val absoluteMaximum = Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
private val absoluteMinimum = Vector3f(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)

fun AABB.calculateMinMax(entities: List<Entity>) {
    if (entities.isEmpty()) {
        min.set(-1f, -1f, -1f)
        max.set(1f, 1f, 1f)
        return
    }
    min.set(absoluteMaximum)
    max.set(absoluteMinimum)
    for (entity in entities) {
        if (!entity.hasComponent(ModelComponent::class.java)) {
            continue
        }
        val minMaxWorld = entity.minMaxWorld
        val currentMin = minMaxWorld.min
        val currentMax = minMaxWorld.max
        min.x = currentMin.x.coerceAtMost(min.x)
        min.y = currentMin.y.coerceAtMost(min.y)
        min.z = currentMin.z.coerceAtMost(min.z)
        max.x = currentMax.x.coerceAtLeast(max.x)
        max.y = currentMax.y.coerceAtLeast(max.y)
        max.z = currentMax.z.coerceAtLeast(max.z)
    }
}