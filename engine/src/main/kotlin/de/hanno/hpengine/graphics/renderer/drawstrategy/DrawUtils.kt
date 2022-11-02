package de.hanno.hpengine.graphics.renderer.drawstrategy

import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.scene.VertexIndexBuffer

import de.hanno.hpengine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.graphics.vertexbuffer.*
import org.lwjgl.opengl.GL40.GL_PATCHES
import org.lwjgl.opengl.GL42.GL_LINES
import org.lwjgl.opengl.GL42.GL_TRIANGLES

enum class RenderingMode {
    Lines,
    Faces
}
enum class PrimitiveType(val type: Int) {
    Lines(GL_LINES),
    Triangles(GL_TRIANGLES),
    Patches(GL_PATCHES)
}

fun VertexIndexBuffer.draw(
    renderBatch: RenderBatch,
    bindIndexBuffer: Boolean,
    primitiveType: PrimitiveType = PrimitiveType.Triangles,
    mode: RenderingMode = RenderingMode.Faces
): Int {
    return indexBuffer.draw(renderBatch.drawElementsIndirectCommand, bindIndexBuffer, primitiveType, mode)
}

fun IndexBuffer.draw(
    drawElementsIndirectCommand: DrawElementsIndirectCommand,
    bindIndexBuffer: Boolean,
    primitiveType: PrimitiveType, mode: RenderingMode
): TriangleCount = drawLinesInstancedBaseVertex(drawElementsIndirectCommand, bindIndexBuffer, mode, primitiveType)

