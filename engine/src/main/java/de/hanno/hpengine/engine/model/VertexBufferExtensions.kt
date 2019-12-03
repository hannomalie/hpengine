package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import org.lwjgl.opengl.ARBIndirectParameters
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL31
import org.lwjgl.opengl.GL42

import org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect

@JvmOverloads
fun VertexBuffer.drawDebugLines(lineWidth: Float = 2f): Int {
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
    GL11.glLineWidth(lineWidth)
    bind()
    GL11.glDrawArrays(GL11.GL_LINES, 0, verticesCount)
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
    return verticesCount
}

@JvmOverloads
fun VertexBuffer.drawDebug(indexBuffer: IndexBuffer, lineWidth: Float = 1f): Int {
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
    GL11.glLineWidth(lineWidth)
    bind()
    drawActually(indexBuffer)
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
    return verticesCount
}

fun VertexBuffer.drawLinesInstancedBaseVertex(indexBuffer: IndexBuffer, command: DrawElementsIndirectCommand): Int {
    return drawLinesInstancedBaseVertex(indexBuffer, command.count, command.primCount, command.firstIndex, command.baseVertex)
}

fun VertexBuffer.drawLinesInstancedBaseVertex(indexBuffer: IndexBuffer?, indexCount: Int, instanceCount: Int, indexOffset: Int, baseVertexIndex: Int): Int {
    bind()
    if (indexBuffer != null) {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        GL11.glLineWidth(1f)
        indexBuffer.bind()
        GL42.glDrawElementsInstancedBaseVertexBaseInstance(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, (4 * indexOffset).toLong(), instanceCount, baseVertexIndex, 0)
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)

    } else {
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, verticesCount, instanceCount)
    }

    return indexCount / 3
}

fun VertexBuffer.drawInstancedBaseVertex(indexBuffer: IndexBuffer, command: DrawElementsIndirectCommand): Int {
    return drawInstancedBaseVertex(indexBuffer, command.count, command.primCount, command.firstIndex, command.baseVertex)
}

@JvmOverloads
fun VertexBuffer.draw(indexBuffer: IndexBuffer? = null): Int {
    bind()
    return drawActually(indexBuffer)
}

/**
 *
 * @return triangleCount that twas drawn
 */
fun VertexBuffer.drawActually(indexBuffer: IndexBuffer?): Int {
    if (indexBuffer != null) {
        indexBuffer.bind()
        val indices = indexBuffer.buffer.asIntBuffer()
        GL11.glDrawElements(GL11.GL_TRIANGLES, indices)
        return indices.capacity() / 3
    } else {
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verticesCount)
        return verticesCount / 3
    }
}

/**
 *
 *
 * @param indexCount
 * @param instanceCount
 * @param indexOffset
 * @param baseVertexIndex the integer index, not the byte offset
 * @return
 */
fun VertexBuffer.drawInstancedBaseVertex(indexBuffer: IndexBuffer?, indexCount: Int, instanceCount: Int, indexOffset: Int, baseVertexIndex: Int): Int {
    bind()
    if (indexBuffer != null) {
        indexBuffer.bind()
        GL42.glDrawElementsInstancedBaseVertexBaseInstance(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, (4 * indexOffset).toLong(), instanceCount, baseVertexIndex, 0)
    } else {
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, verticesCount, instanceCount)
    }

    return indexCount
}

fun VertexBuffer.multiDrawElementsIndirectCount(indexBuffer: IndexBuffer,
                                                commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>,
                                                drawCountBuffer: AtomicCounterBuffer,
                                                maxDrawCount: Int) {
    drawCountBuffer.bindAsParameterBuffer()
    bind()
    indexBuffer.bind()
    commandBuffer.bind()
    ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, 0, maxDrawCount, 0)
    drawCountBuffer.unbind()
    indexBuffer.unbind()
}

fun VertexIndexBuffer.multiDrawElementsIndirectCount(commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>,
                                                     drawCountBuffer: AtomicCounterBuffer,
                                                     maxDrawCount: Int) {
    vertexBuffer.multiDrawElementsIndirectCount(indexBuffer, commandBuffer, drawCountBuffer, maxDrawCount)
}

fun VertexBuffer.multiDrawElementsIndirect(indexBuffer: IndexBuffer, commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>, primitiveCount: Int) {
    bind()
    indexBuffer.bind()
    commandBuffer.bind()
    glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, primitiveCount, 0)
    indexBuffer.unbind()
}

fun VertexIndexBuffer.drawLinesInstancedIndirectBaseVertex(commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>, primitiveCount: Int) {
    vertexBuffer.drawLinesInstancedIndirectBaseVertex(indexBuffer, commandBuffer, primitiveCount)
}

fun VertexBuffer.drawLinesInstancedIndirectBaseVertex(indexBuffer: IndexBuffer, commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>, primitiveCount: Int) {
    bind()
    indexBuffer.bind()
    commandBuffer.bind()
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
    GL11.glLineWidth(1f)
    glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, primitiveCount, 0)
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
}