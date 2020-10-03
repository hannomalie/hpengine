package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.vertexbuffer.IndexBuffer
import org.lwjgl.BufferUtils

class VertexIndexBuffer(gpuContext: GpuContext<*>,
                        indexBufferSizeInIntsCount: Int) {

    var indexBuffer = IndexBuffer(gpuContext, BufferUtils.createIntBuffer(indexBufferSizeInIntsCount))
    private var currentBaseVertex = 0
    private var currentIndexOffset = 0

    fun allocate(elementsCount: Int, indicesCount: Int): VertexIndexOffsets = synchronized(this) {
        VertexIndexOffsets(currentBaseVertex, currentIndexOffset).apply {
            currentBaseVertex += elementsCount
            currentIndexOffset += indicesCount
        }
    }

    fun resetAllocations() {
        currentBaseVertex = 0
        currentIndexOffset = 0
    }

    val vertexArray = mutableListOf<Vertex>()
    val animatedVertexArray = mutableListOf<AnimatedVertex>()

    var vertexStructArray = PersistentMappedStructBuffer(vertexArray.size, gpuContext, { VertexStructPacked() })
    var animatedVertexStructArray = PersistentMappedStructBuffer(animatedVertexArray.size, gpuContext, { AnimatedVertexStructPacked() })

    data class VertexIndexOffsets(val vertexOffset: Int, val indexOffset: Int)
}