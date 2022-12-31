package de.hanno.hpengine.scene

import AnimatedVertexStruktPackedImpl.Companion.type
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.IndexBuffer
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.*
import de.hanno.hpengine.graphics.vertexbuffer.OpenGLIndexBuffer
import org.lwjgl.BufferUtils

context(GraphicsApi)
class VertexIndexBuffer(indexBufferSizeInIntsCount: Int): IVertexIndexBuffer<VertexStruktPacked, AnimatedVertexStruktPacked> {

    override var indexBuffer: IndexBuffer = OpenGLIndexBuffer(BufferUtils.createIntBuffer(indexBufferSizeInIntsCount))
    // TODO: It's invalid to use a single index for two vertex arrays, move animated vertex array out of here
    private var currentBaseVertex = 0
    private var currentIndexOffset = 0

    // TODO: Remove synchronized with lock
    override fun allocate(elementsCount: Int, indicesCount: Int): VertexIndexOffsets = synchronized(this) {
        VertexIndexOffsets(currentBaseVertex, currentIndexOffset).apply {
            currentBaseVertex += elementsCount
            currentIndexOffset += indicesCount
        }
    }

    override fun resetAllocations() {
        currentBaseVertex = 0
        currentIndexOffset = 0
    }

    override var vertexStructArray: TypedGpuBuffer<VertexStruktPacked> = PersistentShaderStorageBuffer(VertexStruktPacked.type.sizeInBytes).typed(
        VertexStruktPacked.type
    )
    override var animatedVertexStructArray: TypedGpuBuffer<AnimatedVertexStruktPacked> = PersistentShaderStorageBuffer(AnimatedVertexStruktPacked.type.sizeInBytes).typed(
        AnimatedVertexStruktPacked.type
    )
}
