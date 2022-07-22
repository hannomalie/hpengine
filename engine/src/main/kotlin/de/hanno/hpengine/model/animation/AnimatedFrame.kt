package de.hanno.hpengine.model.animation

import de.hanno.hpengine.BufferableMatrix4f
import org.joml.Matrix4f

class AnimatedFrame {
    val jointMatrices = (0 until MAX_JOINTS).map {
        IDENTITY_MATRIX
    }.toTypedArray()

    fun setMatrix(pos: Int, jointMatrix: Matrix4f) {
        jointMatrices[pos] = BufferableMatrix4f(jointMatrix)
    }

    companion object {
        private val IDENTITY_MATRIX = BufferableMatrix4f(Matrix4f())
        const val MAX_JOINTS = 150
    }
}