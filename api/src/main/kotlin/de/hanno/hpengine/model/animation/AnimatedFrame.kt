package de.hanno.hpengine.model.animation

import org.joml.Matrix4f

class AnimatedFrame {
    val jointMatrices = (0 until MAX_JOINTS).map {
        Matrix4f()
    }.toTypedArray()

    fun setMatrix(pos: Int, jointMatrix: Matrix4f) {
        jointMatrices[pos].set(jointMatrix)
    }

    companion object {
        const val MAX_JOINTS = 150
    }
}