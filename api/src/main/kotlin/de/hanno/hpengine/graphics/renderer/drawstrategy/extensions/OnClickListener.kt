package de.hanno.hpengine.graphics.renderer.drawstrategy.extensions

import org.joml.Vector2i

interface OnClickListener {
    fun onClick(coordinates: Vector2i, indices: Indices)
}

data class Indices(
    val entityBufferIndex: Int,
    val entityId: Int,
    val meshIndex: Int,
    val materialIndex: Int
)