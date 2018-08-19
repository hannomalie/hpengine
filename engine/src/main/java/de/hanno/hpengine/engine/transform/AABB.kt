package de.hanno.hpengine.engine.transform

import org.joml.Vector3f
import org.joml.Vector4f

data class AABB(var min: Vector3f = Vector3f(), var max: Vector3f = Vector3f()) {
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

    fun contains(position: Vector4f): Boolean {
        return min.x < position.x && max.x > position.x &&
                min.y < position.y && max.y > position.y &&
                min.z < position.z && max.z > position.z
    }
}