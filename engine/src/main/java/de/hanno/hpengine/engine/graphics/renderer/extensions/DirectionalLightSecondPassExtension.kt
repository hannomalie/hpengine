package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.vertexbuffer.draw
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import org.joml.Vector3f
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32

class DirectionalLightSecondPassExtension(
    private val config: Config,
    private val programManager: ProgramManager<OpenGl>,
    private val textureManager: TextureManager,
    private val gpuContext: GpuContext<OpenGl>,
    private val deferredRenderingBuffer: DeferredRenderingBuffer
) : DeferredRenderExtension<OpenGl> {
    private val secondPassDirectionalProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/second_pass_directional_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/second_pass_directional_fragment.glsl"))
    )

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        profiled("Directional light") {

            val viewMatrix = renderState.camera.viewMatrixAsBuffer
            val projectionMatrix = renderState.camera.projectionMatrixAsBuffer

            gpuContext.depthMask = false
            gpuContext.depthTest = false
            gpuContext.blend = true
            gpuContext.blendEquation = BlendMode.FUNC_ADD
            gpuContext.blendFunc(BlendMode.Factor.ONE, BlendMode.Factor.ONE)

//                    GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, gBuffer.depthBufferTexture)
            GL32.glFramebufferTexture(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                deferredRenderingBuffer.depthBufferTexture,
                0
            )
            gpuContext.clearColor(0f, 0f, 0f, 0f)
            gpuContext.clearColorBuffer()

            profiled("Activate DeferredRenderingBuffer textures") {
                gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
                gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
                gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
                gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
                gpuContext.bindTexture(4, GlTextureTarget.TEXTURE_CUBE_MAP, textureManager.cubeMap.id)
                gpuContext.bindTexture(6, GlTextureTarget.TEXTURE_2D, renderState.directionalLightState[0].shadowMapId)
                gpuContext.bindTexture(7, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
                if (!gpuContext.isSupported(BindlessTextures)) {
                    gpuContext.bindTexture(
                        8,
                        GlTextureTarget.TEXTURE_2D,
                        renderState.directionalLightState[0].shadowMapId
                    )
                }
                gpuContext.bindTexture(
                    9,
                    GlTextureTarget.TEXTURE_2D,
                    deferredRenderingBuffer.halfScreenBuffer.getRenderedTexture(2)
                )
                gpuContext.bindTexture(
                    10,
                    GlTextureTarget.TEXTURE_2D,
                    deferredRenderingBuffer.gBuffer.getRenderedTexture(4)
                )
            }

            secondPassDirectionalProgram.use()
            val camTranslation = Vector3f()
            secondPassDirectionalProgram.setUniform("eyePosition", renderState.camera.getTranslation(camTranslation))
            secondPassDirectionalProgram.setUniform(
                "ambientOcclusionRadius",
                config.effects.ambientocclusionRadius
            )
            secondPassDirectionalProgram.setUniform(
                "ambientOcclusionTotalStrength",
                config.effects.ambientocclusionTotalStrength
            )
            secondPassDirectionalProgram.setUniform("screenWidth", config.width.toFloat())
            secondPassDirectionalProgram.setUniform("screenHeight", config.height.toFloat())
            secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            secondPassDirectionalProgram.bindShaderStorageBuffer(2, renderState.directionalLightState)
            profiled("Draw fullscreen buffer") {
                gpuContext.fullscreenBuffer.draw()
            }

        }
    }
}