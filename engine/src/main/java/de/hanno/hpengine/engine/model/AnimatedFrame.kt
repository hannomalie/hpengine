package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.BufferableMatrix4f
import org.joml.Matrix4f
import java.util.Arrays

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