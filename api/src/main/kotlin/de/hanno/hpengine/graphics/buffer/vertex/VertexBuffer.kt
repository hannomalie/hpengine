package de.hanno.hpengine.graphics.buffer.vertex

import java.util.concurrent.CompletableFuture

interface VertexBuffer {
    val verticesCount: Int
    fun bind()
    fun upload(): CompletableFuture<VertexBuffer>
}