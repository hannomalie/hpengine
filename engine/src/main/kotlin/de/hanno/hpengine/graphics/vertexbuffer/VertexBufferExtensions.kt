package de.hanno.hpengine.graphics.vertexbuffer

import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.AtomicCounterBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.PrimitiveType
import de.hanno.hpengine.graphics.renderer.drawstrategy.RenderingMode
import de.hanno.hpengine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.graphics.renderer.pipelines.DrawElementsIndirectCommandStrukt
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentTypedBuffer
import de.hanno.hpengine.scene.VertexIndexBuffer
import org.lwjgl.opengl.ARBIndirectParameters.*
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL31
import org.lwjgl.opengl.GL42

import org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect

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

fun VertexBuffer.drawDebug(indexBuffer: IndexBuffer, lineWidth: Float = 1f): Int {
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
    GL11.glLineWidth(lineWidth)
    bind()
    drawActually(indexBuffer)
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
    return verticesCount
}

typealias TriangleCount = Int
fun IndexBuffer.drawLinesInstancedBaseVertex(
    command: DrawElementsIndirectCommand,
    bindIndexBuffer: Boolean,
    mode: RenderingMode,
    primitiveType: PrimitiveType
): TriangleCount = drawLinesInstancedBaseVertex(
    command.count,
    command.instanceCount,
    command.firstIndex,
    command.baseVertex,
    bindIndexBuffer,
    mode,
    primitiveType
)

fun IndexBuffer.drawLinesInstancedBaseVertex(
    indexCount: Int, instanceCount: Int, indexOffset: Int,
    baseVertexIndex: Int, bindIndexBuffer: Boolean,
    mode: RenderingMode, primitiveType: PrimitiveType
): TriangleCount {
    if (bindIndexBuffer) bind()

    when (mode) {
        RenderingMode.Lines -> {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
            GL11.glLineWidth(1f)
        }
        RenderingMode.Faces -> GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
    }

    GL42.glDrawElementsInstancedBaseVertexBaseInstance(
        primitiveType.type,
        indexCount,
        GL11.GL_UNSIGNED_INT,
        (4 * indexOffset).toLong(),
        instanceCount,
        baseVertexIndex,
        0
    )

    return indexCount / 3
}

fun drawLinesInstancedBaseVertex(indexCount: Int, instanceCount: Int): Int {
    GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, indexCount / 3, instanceCount)
    return indexCount / 3
}

fun IndexBuffer.drawInstancedBaseVertex(
    command: DrawElementsIndirectCommand,
    bindIndexBuffer: Boolean,
    mode: RenderingMode,
    primitiveType: PrimitiveType
): Int {
    return drawInstancedBaseVertex(
        command.count,
        command.instanceCount,
        command.firstIndex,
        command.baseVertex,
        bindIndexBuffer,
        mode,
        primitiveType
    )
}

fun drawInstancedBaseVertex(command: DrawElementsIndirectCommand): Int {
    return drawInstancedBaseVertex(command.count, command.instanceCount, command.firstIndex, command.baseVertex)
}

fun IndexBuffer.drawPatchesInstancedBaseVertex(
    command: DrawElementsIndirectCommand,
    bindIndexBuffer: Boolean,
    mode: RenderingMode,
    primitiveType: PrimitiveType
): Int {
    return drawPatchesInstancedBaseVertex(
        command.count,
        command.instanceCount,
        command.firstIndex,
        command.baseVertex,
        bindIndexBuffer,
        mode,
        primitiveType
    )
}

fun IVertexBuffer.draw(indexBuffer: IndexBuffer? = null): Int {
    bind()
    return drawActually(indexBuffer)
}

/**
 *
 * @return triangleCount that twas drawn
 */
fun IVertexBuffer.drawActually(indexBuffer: IndexBuffer?): Int {
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
fun IndexBuffer.drawInstancedBaseVertex(
    indexCount: Int, instanceCount: Int, indexOffset: Int,
    baseVertexIndex: Int, bindIndexBuffer: Boolean, mode: RenderingMode,
    primitiveType: PrimitiveType
): Int {
    if (bindIndexBuffer) {
        bind()
    }
    if (mode == RenderingMode.Lines) {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        GL11.glLineWidth(1f)
    }
    GL42.glDrawElementsInstancedBaseVertexBaseInstance(
        primitiveType.type,
        indexCount,
        GL11.GL_UNSIGNED_INT,
        (4 * indexOffset).toLong(),
        instanceCount,
        baseVertexIndex,
        0
    )

    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)

    return indexCount / 3
}

fun IndexBuffer.drawPatchesInstancedBaseVertex(
    indexCount: Int, instanceCount: Int, indexOffset: Int,
    baseVertexIndex: Int, bindIndexBuffer: Boolean,
    mode: RenderingMode, primitiveType: PrimitiveType
): Int {
    if (bindIndexBuffer) {
        bind()
    }
    if (mode == RenderingMode.Lines) {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        GL11.glLineWidth(1f)
    }
    GL42.glDrawElementsInstancedBaseVertexBaseInstance(
        primitiveType.type,
        indexCount,
        GL11.GL_UNSIGNED_INT,
        (4 * indexOffset).toLong(),
        instanceCount,
        baseVertexIndex,
        0
    )

    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)

    return indexCount / 3
}

fun drawInstancedBaseVertex(indexCount: Int, instanceCount: Int, indexOffset: Int, baseVertexIndex: Int): Int {
    GL42.glDrawElementsInstancedBaseVertexBaseInstance(
        GL11.GL_TRIANGLES,
        indexCount,
        GL11.GL_UNSIGNED_INT,
        (4 * indexOffset).toLong(),
        instanceCount,
        baseVertexIndex,
        0
    )
    return instanceCount * (indexCount / 3)
}

fun multiDrawElementsIndirectCount(
    indexBuffer: IndexBuffer,
    commandBuffer: PersistentTypedBuffer<DrawElementsIndirectCommandStrukt>,
    drawCountBuffer: AtomicCounterBuffer,
    drawCount: Long = 0,
    maxDrawCount: Int,
    mode: RenderingMode
) {
    drawCountBuffer.bindAsParameterBuffer()
    indexBuffer.bind()
    commandBuffer.persistentMappedBuffer.bind()

    if (mode == RenderingMode.Lines) {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        GL11.glLineWidth(1f)
    }
    profiled("glMultiDrawElementsIndirectCountARB") {
        glMultiDrawElementsIndirectCountARB(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, drawCount, maxDrawCount, 0)
    }
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
    drawCountBuffer.unbind()
    indexBuffer.unbind()
}

fun VertexIndexBuffer.multiDrawElementsIndirectCount(
    commandBuffer: PersistentTypedBuffer<DrawElementsIndirectCommandStrukt>,
    drawCountBuffer: AtomicCounterBuffer,
    drawCount: Long = 0,
    maxDrawCount: Int,
    mode: RenderingMode
) {
    return when (mode) {
        RenderingMode.Lines -> drawLinesInstancedIndirectBaseVertex(indexBuffer, commandBuffer, maxDrawCount, mode)
        RenderingMode.Faces -> multiDrawElementsIndirectCount(
            indexBuffer,
            commandBuffer,
            drawCountBuffer,
            drawCount,
            maxDrawCount,
            mode
        )
    }
}

fun VertexIndexBuffer.multiDrawElementsIndirect(
    commandBuffer: PersistentTypedBuffer<DrawElementsIndirectCommandStrukt>,
    drawCount: Int,
    isDrawLines: Boolean
) {
    if (isDrawLines) {
        drawLinesInstancedIndirectBaseVertex(
            indexBuffer,
            commandBuffer,
            drawCount,
            RenderingMode.Lines
        ) // This might be wrong
    } else {
        multiDrawElementsIndirect(indexBuffer, commandBuffer, drawCount)
    }
}

fun multiDrawElementsIndirect(
    indexBuffer: IndexBuffer,
    commandBuffer: PersistentTypedBuffer<DrawElementsIndirectCommandStrukt>,
    drawCount: Int
) {
    indexBuffer.bind()
    commandBuffer.persistentMappedBuffer.bind()
    glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, drawCount, 0)
    indexBuffer.unbind()
}

fun VertexIndexBuffer.drawLinesInstancedIndirectBaseVertex(
    commandBuffer: PersistentTypedBuffer<DrawElementsIndirectCommandStrukt>, primitiveCount: Int, mode: RenderingMode
) {
    drawLinesInstancedIndirectBaseVertex(indexBuffer, commandBuffer, primitiveCount, mode)
}

fun drawLinesInstancedIndirectBaseVertex(
    indexBuffer: IndexBuffer,
    commandBuffer: PersistentTypedBuffer<DrawElementsIndirectCommandStrukt>,
    primitiveCount: Int,
    mode: RenderingMode
) {
    indexBuffer.bind()
    commandBuffer.persistentMappedBuffer.bind()
    if (mode == RenderingMode.Lines) {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        GL11.glLineWidth(1f)
    }
    glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, primitiveCount, 0)
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
}