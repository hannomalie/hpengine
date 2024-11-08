package de.hanno.hpengine.graphics.buffer.vertex

import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.graphics.TriangleCount
import de.hanno.hpengine.graphics.buffer.AtomicCounterBuffer
import de.hanno.hpengine.graphics.buffer.GpuBuffer
import de.hanno.hpengine.graphics.buffer.TypedGpuBuffer
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.renderer.glValue
import de.hanno.hpengine.renderer.DrawElementsIndirectCommand
import de.hanno.hpengine.renderer.DrawElementsIndirectCommandStrukt
import de.hanno.hpengine.scene.VertexIndexBuffer
import de.hanno.hpengine.toCount
import org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawArraysIndirectCountARB
import org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawElementsIndirectCountARB
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL42
import org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect

fun drawLines(lineWidth: Float = 2f, verticesCount: Int): Int {
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
    GL11.glEnable(GL11.GL_LINE_SMOOTH)
    GL11.glLineWidth(lineWidth)
    GL11.glDrawArrays(GL11.GL_LINES, 0, verticesCount)
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
    return verticesCount
}

fun GpuBuffer.drawElementsInstancedBaseVertex(
    command: DrawElementsIndirectCommand,
    bindIndexBuffer: Boolean,
    mode: RenderingMode,
    primitiveType: PrimitiveType
): TriangleCount = drawElementsInstancedBaseVertex(
    command.count.value.toInt(),
    command.instanceCount.value.toInt(),
    command.firstIndex.value.toInt(),
    command.baseVertex.value.toInt(),
    bindIndexBuffer,
    mode,
    primitiveType
)

/**
 *
 *
 * @param indexCount
 * @param instanceCount
 * @param indexOffset
 * @param baseVertexIndex the integer index, not the byte offset
 * @return
 */
fun GpuBuffer.drawElementsInstancedBaseVertex(
    indexCount: Int, instanceCount: Int, indexOffset: Int,
    baseVertexIndex: Int, bindIndexBuffer: Boolean, mode: RenderingMode,
    primitiveType: PrimitiveType
): TriangleCount {
    if (bindIndexBuffer) {
        bind()
    }

    // TODO: Use gpuContext here
    when(mode) {
        RenderingMode.Lines -> GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        RenderingMode.Fill -> GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
    }
    GL42.glDrawElementsInstancedBaseVertexBaseInstance(
        primitiveType.glValue,
        indexCount,
        GL11.GL_UNSIGNED_INT,
        (Int.SIZE_BYTES * indexOffset).toLong(),
        instanceCount,
        baseVertexIndex,
        0
    )
    return ElementCount(instanceCount * (indexCount / 3))
}

fun VertexIndexBuffer<*>.drawElementsIndirectCount(
    commandBuffer: TypedGpuBuffer<DrawElementsIndirectCommandStrukt>,
    drawCountBuffer: AtomicCounterBuffer,
    drawCount: ElementCount = 0.toCount(),
    maxDrawCount: ElementCount,
    mode: RenderingMode
) = when (mode) {
    RenderingMode.Lines -> drawElementsIndirect(indexBuffer, commandBuffer, maxDrawCount.value.toInt(), mode)
    RenderingMode.Fill -> drawElementsIndirectCount(
        indexBuffer,
        commandBuffer,
        drawCountBuffer,
        drawCount,
        maxDrawCount,
        mode
    )
}

fun drawElementsIndirectCount(
    indexBuffer: GpuBuffer,
    commandBuffer: TypedGpuBuffer<DrawElementsIndirectCommandStrukt>,
    drawCountBuffer: AtomicCounterBuffer,
    drawCount: ElementCount = 0.toCount(),
    maxDrawCount: ElementCount,
    mode: RenderingMode
) {
    drawCountBuffer.bindAsParameterBuffer()
    indexBuffer.bind()
    commandBuffer.bind()

    // TODO: Use gpuContext here
    when(mode) {
        RenderingMode.Lines -> GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        RenderingMode.Fill -> GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
    }
    glMultiDrawElementsIndirectCountARB(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, drawCount.value, maxDrawCount.value.toInt(), 0)
    drawCountBuffer.unbind()
    indexBuffer.unbind()
}
fun drawArraysIndirectCount(
    commandBuffer: TypedGpuBuffer<DrawElementsIndirectCommandStrukt>,
    drawCountBuffer: AtomicCounterBuffer,
    drawCount: ElementCount = 0.toCount(),
    maxDrawCount: ElementCount,
    mode: RenderingMode
) {
    drawCountBuffer.bindAsParameterBuffer()
    commandBuffer.bind()

    // TODO: Use gpuContext here
    when(mode) {
        RenderingMode.Lines -> GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        RenderingMode.Fill -> GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
    }
    glMultiDrawArraysIndirectCountARB(GL11.GL_TRIANGLES, 0L, drawCount.value, maxDrawCount.value.toInt(), 0)
    drawCountBuffer.unbind()
}

fun drawElementsIndirect(
    indexBuffer: GpuBuffer?,
    commandBuffer: TypedGpuBuffer<DrawElementsIndirectCommandStrukt>,
    primitiveCount: Int,
    mode: RenderingMode
) {
    indexBuffer?.bind()
    commandBuffer.bind()
    // TODO: Use gpuContext here
    when(mode) {
        RenderingMode.Lines -> GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
        RenderingMode.Fill -> GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
    }
    glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, primitiveCount, 0)
}