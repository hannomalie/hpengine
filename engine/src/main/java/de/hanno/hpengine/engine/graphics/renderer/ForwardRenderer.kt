package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawUtils
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.Shader.ShaderSourceFactory.getShaderSource
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.model.Update
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBClearTexture
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL40.*
import java.io.File

class ForwardRenderer(renderState: TripleBuffer<RenderState>, val deferredRenderingBuffer: DeferredRenderingBuffer, val engineContext: EngineContext): RenderExtension {

    val firstpassDefaultVertexshaderSource = getShaderSource(File(Shader.getDirectory() + "mvp_entitybuffer_vertex.glsl"))
    val firstpassDefaultFragmentshaderSource = getShaderSource(File(Shader.getDirectory() + "forward_fragment.glsl"))

    val programStatic = engineContext.programManager.getProgram(firstpassDefaultVertexshaderSource, firstpassDefaultFragmentshaderSource, Defines())

    override fun renderFirstPass(backend: Backend, gpuContext: GpuContext, firstPassResult: FirstPassResult, renderState: RenderState) {
        engineContext.gpuContext.clearColor(0f,0f,0f,1f)
        deferredRenderingBuffer.forwardBuffer.use(true)

//        ARBClearTexture.glClearTexImage(deferredRenderingBuffer.forwardBuffer.getRenderedTexture(0), 0, GL30.GL_RGBA, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER)
//        ARBClearTexture.glClearTexImage(deferredRenderingBuffer.forwardBuffer.getRenderedTexture(1), 0, GL30.GL_RGBA, GL11.GL_UNSIGNED_BYTE, ONE_BUFFER)
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, deferredRenderingBuffer.depthBufferTexture)
        engineContext.gpuContext.depthMask(false)
        engineContext.gpuContext.depthFunc(GlDepthFunc.LEQUAL)
        engineContext.gpuContext.enable(GlCap.BLEND)
        engineContext.gpuContext.blendEquation(BlendMode.FUNC_ADD)
        GL14.glBlendFuncSeparate(BlendMode.Factor.ONE.glFactor, BlendMode.Factor.ONE.glFactor, BlendMode.Factor.ZERO.glFactor, BlendMode.Factor.ONE_MINUS_SRC_ALPHA.glFactor)
//        glBlendFunc(BlendMode.Factor.ONE.glFactor, BlendMode.Factor.ONE.glFactor)
//        glBlendFunci(1, BlendMode.Factor.ZERO.glFactor, BlendMode.Factor.ONE_MINUS_SRC_ALPHA.glFactor)

        programStatic.use()
        programStatic.bindShaderStorageBuffer(1, renderState.materialBuffer)
        programStatic.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
        programStatic.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        programStatic.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)

        programStatic.setUniform("lightDirection", renderState.directionalLightState.directionalLightDirection)

        for (batch in renderState.renderBatchesStatic) {
            if(!batch.materialInfo.transparencyType.needsForwardRendering) { continue }
            val isStatic = batch.update == Update.STATIC
            val currentVerticesCount = DrawUtils.draw(engineContext.gpuContext, renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, batch, programStatic, false, false)

            //                TODO: Count this somehow?
            //                firstPassResult.verticesDrawn += currentVerticesCount;
            //                if (currentVerticesCount > 0) {
            //                    firstPassResult.entitiesDrawn++;
            //                }
        }
        engineContext.gpuContext.disable(GlCap.BLEND)
        deferredRenderingBuffer.forwardBuffer.unuse()
    }

    companion object {
        var ZERO_BUFFER = BufferUtils.createFloatBuffer(4)
        var ONE_BUFFER = BufferUtils.createFloatBuffer(4)
        init {
            ZERO_BUFFER.put(0f)
            ZERO_BUFFER.put(0f)
            ZERO_BUFFER.put(0f)
            ZERO_BUFFER.put(0f)
            ZERO_BUFFER.rewind()

            ONE_BUFFER.put(1f)
            ONE_BUFFER.put(1f)
            ONE_BUFFER.put(1f)
            ONE_BUFFER.put(1f)
            ONE_BUFFER.rewind()
        }
    }
}