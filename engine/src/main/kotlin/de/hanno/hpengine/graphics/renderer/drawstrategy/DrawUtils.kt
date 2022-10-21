package de.hanno.hpengine.graphics.renderer.drawstrategy

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.shader.ComputeProgram
import de.hanno.hpengine.scene.VertexIndexBuffer
import de.hanno.hpengine.util.Util
import org.lwjgl.opengl.GL15

import de.hanno.hpengine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.graphics.vertexbuffer.*
import org.jetbrains.kotlin.util.profile
import org.lwjgl.opengl.GL40.GL_PATCHES
import org.lwjgl.opengl.GL42.GL_LINES
import org.lwjgl.opengl.GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
import org.lwjgl.opengl.GL42.GL_TRIANGLES
import org.lwjgl.opengl.GL42.glMemoryBarrier

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

