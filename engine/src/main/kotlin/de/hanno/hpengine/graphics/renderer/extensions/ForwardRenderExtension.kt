package de.hanno.hpengine.graphics.renderer.extensions

import de.hanno.hpengine.backend.Backend

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.constants.BlendMode
import de.hanno.hpengine.graphics.renderer.constants.Capability
import de.hanno.hpengine.graphics.renderer.constants.DepthFunc
import de.hanno.hpengine.graphics.renderer.drawstrategy.*
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.shader.safePut
import de.hanno.hpengine.graphics.shader.useAndBind
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.ressources.FileBasedCodeSource
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32
import org.lwjgl.opengl.GL40.GL_ONE
import org.lwjgl.opengl.GL40.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL40.GL_ZERO
import org.lwjgl.opengl.GL40.glBlendFuncSeparatei
import org.lwjgl.opengl.GL40.glBlendFunci

class ForwardRenderExtension(
    val config: Config,
    val gpuContext: GpuContext,
    val programManager: ProgramManager,
    val deferredRenderingBuffer: DeferredRenderingBuffer
): DeferredRenderExtension {

    val firstpassDefaultVertexshaderSource = FileBasedCodeSource(config.engineDir.resolve("shaders/" + "first_pass_vertex.glsl"))
    val firstpassDefaultFragmentshaderSource = FileBasedCodeSource(config.engineDir.resolve("shaders/" + "forward_fragment.glsl"))

    val programStatic = programManager.getProgram(
        firstpassDefaultVertexshaderSource,
        firstpassDefaultFragmentshaderSource,
        StaticFirstPassUniforms(gpuContext),
        Defines()
    )

    override fun renderFirstPass(backend: Backend, gpuContext: GpuContext, firstPassResult: FirstPassResult, renderState: RenderState) {
        deferredRenderingBuffer.forwardBuffer.use(gpuContext, false)

        GL30.glClearBufferfv(GL11.GL_COLOR, 0, floatArrayOf(0f, 0f, 0f, 0f))
        GL30.glClearBufferfv(GL11.GL_COLOR, 1, floatArrayOf(1f, 1f, 1f, 1f))
//        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, deferredRenderingBuffer.depthBufferTexture)
        GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, deferredRenderingBuffer.depthBufferTexture, 0)
        gpuContext.depthMask = false
        gpuContext.depthFunc = DepthFunc.LEQUAL
        gpuContext.blend = true
        gpuContext.blendEquation = BlendMode.FUNC_ADD
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
        for (batch in renderState.renderBatchesStatic.filter { it.material.transparencyType.needsForwardRendering }) {
            programStatic.setTextureUniforms(gpuContext, batch.material.maps)
            renderState.vertexIndexBufferStatic.indexBuffer.draw(
                batch.drawElementsIndirectCommand, bindIndexBuffer = false,
                primitiveType = PrimitiveType.Triangles, mode = RenderingMode.Faces
            )
        }
        gpuContext.disable(Capability.BLEND)
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