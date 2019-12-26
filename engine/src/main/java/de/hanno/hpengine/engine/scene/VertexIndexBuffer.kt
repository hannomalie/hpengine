package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.vertexbuffer.DataChannels
import de.hanno.hpengine.engine.vertexbuffer.IndexBuffer
import de.hanno.hpengine.engine.vertexbuffer.VertexBuffer
import org.lwjgl.BufferUtils
import java.util.EnumSet

class VertexIndexBuffer(gpuContext: GpuContext<*>,
                        vertexBufferSizeInFloatsCount: Int,
                        indexBufferSizeInIntsCount: Int,
                        channels: EnumSet<DataChannels>) {

    var vertexBuffer = VertexBuffer(gpuContext, BufferUtils.createFloatBuffer(vertexBufferSizeInFloatsCount), channels)
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