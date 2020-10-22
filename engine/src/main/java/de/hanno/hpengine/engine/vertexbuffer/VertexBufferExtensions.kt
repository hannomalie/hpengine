package de.hanno.hpengine.engine.vertexbuffer

import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.PrimitiveMode
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import org.lwjgl.opengl.ARBIndirectParameters
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL31
import org.lwjgl.opengl.GL42

import org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect

@JvmOverloads
fun VertexBuffer.drawLines(lineWidth: Float = 2f): Int {
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
    GL11.glLineWidth(lineWidth)
    bind()
    GL11.glDrawArrays(GL11.GL_LINES, 0, verticesCount)
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
    return verticesCount
}

fun drawLines(lineWidth: Float = 2f, verticesCount: Int): Int {
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
    GL11.glEnable(GL11.GL_LINE_SMOOTH)
    GL11.glLineWidth(lineWidth)
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

fun IndexBuffer.drawLinesInstancedBaseVertex(command: DrawElementsIndirectCommand, bindIndexBuffer: Boolean): Int {
    return this.drawLinesInstancedBaseVertex(command.count, command.primCount, command.firstIndex, command.baseVertex, bindIndexBuffer)
}

fun IndexBuffer.drawLinesInstancedBaseVertex(indexCount: Int, instanceCount: Int, indexOffset: Int, baseVertexIndex: Int, bindIndexBuffer: Boolean = true): Int {
    if(bindIndexBuffer) bind()

    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
    GL11.glLineWidth(1f)
    GL42.glDrawElementsInstancedBaseVertexBaseInstance(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, (4 * indexOffset).toLong(), instanceCount, baseVertexIndex, 0)
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)

    return indexCount / 3
}

fun drawLinesInstancedBaseVertex(indexCount: Int, instanceCount: Int): Int {
    GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, indexCount/3, instanceCount)
    return indexCount / 3
}

fun IndexBuffer.drawInstancedBaseVertex(command: DrawElementsIndirectCommand, bindIndexBuffer: Boolean): Int {
    return drawInstancedBaseVertex(command.count, command.primCount, command.firstIndex, command.baseVertex, bindIndexBuffer)
}
fun drawInstancedBaseVertex(command: DrawElementsIndirectCommand): Int {
    return drawInstancedBaseVertex(command.count, command.primCount, command.firstIndex, command.baseVertex)
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
fun IndexBuffer.drawInstancedBaseVertex(indexCount: Int, instanceCount: Int, indexOffset: Int, baseVertexIndex: Int, bindIndexBuffer: Boolean): Int {
    if (bindIndexBuffer) {
        bind()
    }
    GL42.glDrawElementsInstancedBaseVertexBaseInstance(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, (4 * indexOffset).toLong(), instanceCount, baseVertexIndex, 0)

    return indexCount/3
}
fun drawInstancedBaseVertex(indexCount: Int, instanceCount: Int, indexOffset: Int, baseVertexIndex: Int): Int {
    GL42.glDrawElementsInstancedBaseVertexBaseInstance(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, (4 * indexOffset).toLong(), instanceCount, baseVertexIndex, 0)
    return instanceCount * (indexCount/3)
}

fun multiDrawElementsIndirectCount(indexBuffer: IndexBuffer,
                                                commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>,
                                                drawCountBuffer: AtomicCounterBuffer,
                                                drawCount: Long = 0,
                                                maxDrawCount: Int) {
    drawCountBuffer.bindAsParameterBuffer()
    indexBuffer.bind()
    commandBuffer.bind()
    ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, drawCount, maxDrawCount, 0)
    drawCountBuffer.unbind()
    indexBuffer.unbind()
}

fun VertexIndexBuffer.multiDrawElementsIndirectCount(commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>,
                                                     drawCountBuffer: AtomicCounterBuffer,
                                                     drawCount: Long = 0,
                                                     maxDrawCount: Int,
                                                     mode: PrimitiveMode) {
    return when(mode) {
        PrimitiveMode.Lines -> drawLinesInstancedIndirectBaseVertex(indexBuffer, commandBuffer, maxDrawCount)
        PrimitiveMode.Triangles -> multiDrawElementsIndirectCount(indexBuffer, commandBuffer, drawCountBuffer, drawCount, maxDrawCount)
    }
}

fun VertexIndexBuffer.multiDrawElementsIndirect(commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>,
                                                     drawCount: Int,
                                                     isDrawLines: Boolean) {
    if(isDrawLines) {
        drawLinesInstancedIndirectBaseVertex(indexBuffer, commandBuffer, drawCount) // This might be wrong
    } else {
        multiDrawElementsIndirect(indexBuffer, commandBuffer, drawCount)
    }
}

fun multiDrawElementsIndirect(indexBuffer: IndexBuffer, commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>, drawCount: Int) {
    indexBuffer.bind()
    commandBuffer.bind()
    glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, drawCount, 0)
    indexBuffer.unbind()
}

fun VertexIndexBuffer.drawLinesInstancedIndirectBaseVertex(commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>, primitiveCount: Int) {
    drawLinesInstancedIndirectBaseVertex(indexBuffer, commandBuffer, primitiveCount)
}

fun drawLinesInstancedIndirectBaseVertex(indexBuffer: IndexBuffer, commandBuffer: PersistentMappedStructBuffer<DrawElementsIndirectCommand>, primitiveCount: Int) {
    indexBuffer.bind()
    commandBuffer.bind()
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
    GL11.glLineWidth(1f)
    glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, primitiveCount, 0)
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
}