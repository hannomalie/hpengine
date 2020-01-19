package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.Update
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL40.*
import java.io.File

class ForwardRenderExtension(val engineContext: EngineContext<OpenGl>): RenderExtension<OpenGl> {
    val deferredRenderingBuffer: DeferredRenderingBuffer = engineContext.deferredRenderingBuffer

    val firstpassDefaultVertexshaderSource = getShaderSource(File(Shader.directory + "first_pass_vertex.glsl"))
    val firstpassDefaultFragmentshaderSource = getShaderSource(File(Shader.directory + "forward_fragment.glsl"))

    val programStatic = engineContext.programManager.getProgram(firstpassDefaultVertexshaderSource, firstpassDefaultFragmentshaderSource)

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {
        deferredRenderingBuffer.forwardBuffer.use(gpuContext, false)

        GL30.glClearBufferfv(GL11.GL_COLOR, 0, floatArrayOf(0f,0f,0f,0f))
        GL30.glClearBufferfv(GL11.GL_COLOR, 1, floatArrayOf(1f,1f,1f,1f))
//        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, deferredRenderingBuffer.depthBufferTexture)
        GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, deferredRenderingBuffer.depthBufferTexture, 0)
        engineContext.gpuContext.depthMask(false)
        engineContext.gpuContext.depthFunc(GlDepthFunc.LEQUAL)
        engineContext.gpuContext.enable(GlCap.BLEND)
        engineContext.gpuContext.blendEquation(BlendMode.FUNC_ADD)
        glBlendFunci(0, GL_ONE, GL_ONE)
        glBlendFuncSeparatei(1, GL_ZERO, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE)

        programStatic.use()
        programStatic.bindShaderStorageBuffer(1, renderState.materialBuffer)
        programStatic.bindShaderStorageBuffer(2, renderState.directionalLightState)
        programStatic.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
        programStatic.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        programStatic.setUniformAsMatrix4("projectionMatrix", renderState.camera.projectionMatrixAsBuffer)
        programStatic.setUniformAsMatrix4("viewProjectionMatrix", renderState.camera.viewProjectionMatrixAsBuffer)

        for (batch in renderState.renderBatchesStatic) {
            if(!batch.materialInfo.transparencyType.needsForwardRendering) { continue }
            val isStatic = batch.update == Update.STATIC
            programStatic.setTextureUniforms(gpuContext, batch.materialInfo.maps)
            val currentVerticesCount = draw(renderState.vertexIndexBufferStatic.vertexBuffer, renderState.vertexIndexBufferStatic.indexBuffer, batch, programStatic, false, false)

        }
        engineContext.gpuContext.disable(GlCap.BLEND)
        deferredRenderingBuffer.forwardBuffer.unUse()
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