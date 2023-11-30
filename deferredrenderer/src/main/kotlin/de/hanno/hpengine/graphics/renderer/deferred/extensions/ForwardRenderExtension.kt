package de.hanno.hpengine.graphics.renderer.deferred.extensions

import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.buffers.safePut
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.BlendMode
import de.hanno.hpengine.graphics.constants.BlendMode.Factor.*
import de.hanno.hpengine.graphics.constants.DepthFunc
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.forward.StaticFirstPassUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.shader.using
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.ressources.FileBasedCodeSource
import org.koin.core.annotation.Single
import org.lwjgl.BufferUtils

@Single(binds = [ForwardRenderExtension::class, DeferredRenderExtension::class])
class ForwardRenderExtension(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val programManager: ProgramManager,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
): DeferredRenderExtension {
    override val renderPriority = 2000

    val firstpassDefaultVertexshaderSource = FileBasedCodeSource(config.engineDir.resolve("shaders/" + "first_pass_vertex.glsl"))
    val firstpassDefaultFragmentshaderSource = FileBasedCodeSource(config.engineDir.resolve("shaders/" + "forward_fragment.glsl"))

    val programStatic = programManager.getProgram(
        firstpassDefaultVertexshaderSource,
        firstpassDefaultFragmentshaderSource,
        StaticFirstPassUniforms(graphicsApi),
        Defines()
    )

    override fun renderFirstPass(renderState: RenderState) = graphicsApi.run {
        deferredRenderingBuffer.forwardBuffer.use(false)

        clearColorBuffer(0, floatArrayOf(0f, 0f, 0f, 0f))
        clearColorBuffer(1, floatArrayOf(1f, 1f, 1f, 1f))
//        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, deferredRenderingBuffer.depthBufferTexture)
        framebufferDepthTexture(deferredRenderingBuffer.depthBufferTexture, 0)
        depthMask = false
        depthFunc = DepthFunc.LEQUAL
        blend = true
        blendEquation = BlendMode.FUNC_ADD
        blendFunction(0, ONE, ONE)
        blendFunctionRGBAlpha(1, ZERO, ONE_MINUS_SRC_ALPHA, ONE, ONE)

        val entitiesState = renderState[entitiesStateHolder.entitiesState]

        val camera = renderState[primaryCameraStateHolder.camera]
        using(programStatic) { uniforms ->
            uniforms.vertices = entitiesState.vertexIndexBufferStatic.vertexStructArray
            uniforms.materials = entitiesState.materialBuffer
            uniforms.entities = entitiesState.entitiesBuffer
            programStatic.bindShaderStorageBuffer(2, renderState[directionalLightStateHolder.lightState])
            uniforms.viewMatrix.safePut(camera.viewMatrixAsBuffer)
            uniforms.projectionMatrix.safePut(camera.projectionMatrixAsBuffer)
            uniforms.viewProjectionMatrix.safePut(camera.viewProjectionMatrixAsBuffer)
        }

        entitiesState.vertexIndexBufferStatic.indexBuffer.bind()
        for (batch in entitiesState.renderBatchesStatic.filter { it.material.transparencyType.needsForwardRendering }) {
            programStatic.setTextureUniforms(graphicsApi, batch.material.maps)
            entitiesState.vertexIndexBufferStatic.indexBuffer.draw(
                batch.drawElementsIndirectCommand, bindIndexBuffer = false,
                primitiveType = PrimitiveType.Triangles, mode = RenderingMode.Fill
            )
        }
        blend = false
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