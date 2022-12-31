package de.hanno.hpengine.graphics.renderer.picking

import org.joml.Vector2i

// TODO: This depends on Indices, which depends on the semantics of deferred renderer, make it generic
interface OnClickListener {
    fun onClick(coordinates: Vector2i, indices: Indices)
}

data class Indices(
    val entityBufferIndex: Int,
    val entityId: Int,
    val meshIndex: Int,
    val materialIndex: Int
)