package de.hanno.hpengine.engine.graphics.renderer.drawstrategy

import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline
import de.hanno.hpengine.engine.graphics.shader.ComputeProgram
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.vertexbuffer.IndexBuffer
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.util.Util
import org.lwjgl.opengl.GL15

import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DrawElementsIndirectCommand
import de.hanno.hpengine.engine.vertexbuffer.drawInstancedBaseVertex
import de.hanno.hpengine.engine.vertexbuffer.drawLinesInstancedBaseVertex
import de.hanno.hpengine.engine.vertexbuffer.drawPatchesInstancedBaseVertex
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

fun VertexIndexBuffer.draw(renderBatch: RenderBatch, program: Program<*>, bindIndexBuffer: Boolean = true, primitiveType: PrimitiveType = PrimitiveType.Triangles, mode: RenderingMode = RenderingMode.Faces): Int {
    return indexBuffer.draw(renderBatch, program, bindIndexBuffer, primitiveType, mode)
}

fun IndexBuffer.draw(renderBatch: RenderBatch, program: Program<*>, bindIndexBuffer: Boolean = true, primitiveType: PrimitiveType = PrimitiveType.Triangles, mode: RenderingMode = RenderingMode.Faces): Int {
    return actuallyDraw(renderBatch, program, bindIndexBuffer, primitiveType, mode)
}

fun IndexBuffer.actuallyDraw(entityBufferIndex: Int, drawElementsIndirectCommand: DrawElementsIndirectCommand,
                             program: Program<*>, bindIndexBuffer: Boolean = true, primitiveType: PrimitiveType, mode: RenderingMode): Int {

    program.setUniform("entityBaseIndex", 0)
    program.setUniform("entityIndex", entityBufferIndex)
    program.setUniform("indirect", false)

    return when(primitiveType) {
        PrimitiveType.Lines -> drawLinesInstancedBaseVertex(drawElementsIndirectCommand, bindIndexBuffer, mode, primitiveType)
        PrimitiveType.Triangles -> drawInstancedBaseVertex(drawElementsIndirectCommand, bindIndexBuffer, mode, primitiveType)
        PrimitiveType.Patches -> drawPatchesInstancedBaseVertex(drawElementsIndirectCommand, bindIndexBuffer, mode, primitiveType)
    }
}

fun IndexBuffer.actuallyDraw(renderBatch: RenderBatch, program: Program<*>, bindIndexBuffer: Boolean, primitiveType: PrimitiveType, mode: RenderingMode): Int {
    return actuallyDraw(renderBatch.entityBufferIndex, renderBatch.drawElementsIndirectCommand, program, bindIndexBuffer, primitiveType, mode)
}

fun renderHighZMap(gpuContext: GpuContext<*>, baseDepthTexture: Int, baseWidth: Int, baseHeight: Int, highZTexture: Int, highZProgram: ComputeProgram) {
    profile("HighZ map calculation") {
        highZProgram.use()
        var lastWidth = baseWidth
        var lastHeight = baseHeight
        var currentWidth = lastWidth / 2
        var currentHeight = lastHeight / 2
        val mipMapCount = Util.calculateMipMapCount(currentWidth, currentHeight)
        for (mipmapTarget in 0 until mipMapCount) {
            highZProgram.setUniform("width", currentWidth)
            highZProgram.setUniform("height", currentHeight)
            highZProgram.setUniform("lastWidth", lastWidth)
            highZProgram.setUniform("lastHeight", lastHeight)
            highZProgram.setUniform("mipmapTarget", mipmapTarget)
            if (mipmapTarget == 0) {
                gpuContext.bindTexture(0, TEXTURE_2D, baseDepthTexture)
            } else {
                gpuContext.bindTexture(0, TEXTURE_2D, highZTexture)
            }
            gpuContext.bindImageTexture(1, highZTexture, mipmapTarget, false, 0, GL15.GL_READ_WRITE, Pipeline.HIGHZ_FORMAT)
            val num_groups_x = Math.max(1, (currentWidth + 7) / 8)
            val num_groups_y = Math.max(1, (currentHeight + 7) / 8)
            highZProgram.dispatchCompute(num_groups_x, num_groups_y, 1)
            lastWidth = currentWidth
            lastHeight = currentHeight
            currentWidth /= 2
            currentHeight /= 2
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT)
            //            glMemoryBarrier(GL_ALL_BARRIER_BITS);
        }
    }
}
