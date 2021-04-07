package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.engine.graphics.shader.safePut
import de.hanno.hpengine.engine.graphics.shader.useAndBind
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32
import org.lwjgl.opengl.GL40.GL_ONE
import org.lwjgl.opengl.GL40.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL40.GL_ZERO
import org.lwjgl.opengl.GL40.glBlendFuncSeparatei
import org.lwjgl.opengl.GL40.glBlendFunci

class ForwardRenderExtension(val engineContext: EngineContext): RenderExtension<OpenGl> {
    val deferredRenderingBuffer: DeferredRenderingBuffer = engineContext.deferredRenderingBuffer

    val firstpassDefaultVertexshaderSource = FileBasedCodeSource(engineContext.config.engineDir.resolve("shaders/" + "first_pass_vertex.glsl"))
    val firstpassDefaultFragmentshaderSource = FileBasedCodeSource(engineContext.config.engineDir.resolve("shaders/" + "forward_fragment.glsl"))

    val programStatic = engineContext.programManager.getProgram(firstpassDefaultVertexshaderSource, firstpassDefaultFragmentshaderSource, StaticFirstPassUniforms(engineContext.gpuContext))

    override fun renderFirstPass(backend: Backend<OpenGl>, gpuContext: GpuContext<OpenGl>, firstPassResult: FirstPassResult, renderState: RenderState) {
        deferredRenderingBuffer.forwardBuffer.use(gpuContext, false)

        GL30.glClearBufferfv(GL11.GL_COLOR, 0, floatArrayOf(0f, 0f, 0f, 0f))
        GL30.glClearBufferfv(GL11.GL_COLOR, 1, floatArrayOf(1f, 1f, 1f, 1f))
//        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, deferredRenderingBuffer.depthBufferTexture)
        GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, deferredRenderingBuffer.depthBufferTexture, 0)
        engineContext.gpuContext.depthMask = false
        engineContext.gpuContext.depthFunc = GlDepthFunc.LEQUAL
        engineContext.gpuContext.blend = true
        engineContext.gpuContext.blendEquation = BlendMode.FUNC_ADD
        glBlendFunci(0, GL_ONE, GL_ONE)
        glBlendFuncSeparatei(1, GL_ZERO, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE)

        programStatic.useAndBind { uniforms ->
            uniforms.vertices = renderState.entitiesState.vertexIndexBufferStatic.vertexStructArray
            uniforms.materials = renderState.materialBuffer
            uniforms.entities = renderState.entitiesBuffer
            programStatic.bindShaderStorageBuffer(2, renderState.directionalLightState)
            uniforms.viewMatrix.safePut(renderState.camera.viewMatrixAsBuffer)
            uniforms.projectionMatrix.safePut(renderState.camera.projectionMatrixAsBuffer)
            uniforms.viewProjectionMatrix.safePut(renderState.camera.viewProjectionMatrixAsBuffer)
        }

        renderState.vertexIndexBufferStatic.indexBuffer.bind()
        for (batch in renderState.renderBatchesStatic.filter { it.materialInfo.transparencyType.needsForwardRendering }) {
            programStatic.setTextureUniforms(batch.materialInfo.maps)
            val currentVerticesCount = renderState.vertexIndexBufferStatic.indexBuffer.draw(batch, programStatic, bindIndexBuffer = false)
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