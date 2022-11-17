package de.hanno.hpengine.graphics.vertexbuffer

import java.util.concurrent.CompletableFuture

interface IVertexBuffer {
    val verticesCount: Int
    fun bind()
    fun upload(): CompletableFuture<IVertexBuffer>
}