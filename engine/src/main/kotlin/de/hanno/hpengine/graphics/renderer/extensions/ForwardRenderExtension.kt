package de.hanno.hpengine.graphics.renderer.extensions

import de.hanno.hpengine.artemis.EntitiesStateHolder
import de.hanno.hpengine.artemis.PrimaryCameraStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.graphics.renderer.constants.BlendMode
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

context(GpuContext)
class ForwardRenderExtension(
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
        StaticFirstPassUniforms(),
        Defines()
    )

    override fun renderFirstPass(renderState: RenderState) {
        deferredRenderingBuffer.forwardBuffer.use(false)

        GL30.glClearBufferfv(GL11.GL_COLOR, 0, floatArrayOf(0f, 0f, 0f, 0f))
        GL30.glClearBufferfv(GL11.GL_COLOR, 1, floatArrayOf(1f, 1f, 1f, 1f))
//        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, deferredRenderingBuffer.depthBufferTexture)
        GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, deferredRenderingBuffer.depthBufferTexture, 0)
        depthMask = false
        depthFunc = DepthFunc.LEQUAL
        blend = true
        blendEquation = BlendMode.FUNC_ADD
        glBlendFunci(0, GL_ONE, GL_ONE)
        glBlendFuncSeparatei(1, GL_ZERO, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE)

        val entitiesState = renderState[entitiesStateHolder.entitiesState]

        val camera = renderState[primaryCameraStateHolder.camera]
        programStatic.useAndBind { uniforms ->
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
            programStatic.setTextureUniforms(batch.material.maps)
            entitiesState.vertexIndexBufferStatic.indexBuffer.draw(
                batch.drawElementsIndirectCommand, bindIndexBuffer = false,
                primitiveType = PrimitiveType.Triangles, mode = RenderingMode.Faces
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