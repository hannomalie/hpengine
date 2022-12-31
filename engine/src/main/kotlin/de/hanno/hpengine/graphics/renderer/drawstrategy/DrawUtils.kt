package de.hanno.hpengine.graphics.renderer.drawstrategy

import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.graphics.buffer.IndexBuffer
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.buffer.vertex.TriangleCount
import de.hanno.hpengine.graphics.buffer.vertex.drawInstancedBaseVertex
import de.hanno.hpengine.scene.VertexIndexBuffer

fun VertexIndexBuffer.draw(
    renderBatch: RenderBatch,
    bindIndexBuffer: Boolean,
    primitiveType: PrimitiveType = PrimitiveType.Triangles,
    mode: RenderingMode = RenderingMode.Fill
): Int {
    return indexBuffer.draw(renderBatch.drawElementsIndirectCommand, bindIndexBuffer, primitiveType, mode)
}

fun IndexBuffer.draw(
    drawElementsIndirectCommand: DrawElementsIndirectCommand,
    bindIndexBuffer: Boolean,
    primitiveType: PrimitiveType,
    mode: RenderingMode
): TriangleCount = drawInstancedBaseVertex(drawElementsIndirectCommand, bindIndexBuffer, mode, primitiveType)

