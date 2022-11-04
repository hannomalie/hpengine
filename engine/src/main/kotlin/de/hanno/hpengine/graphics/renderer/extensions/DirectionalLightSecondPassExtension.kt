package de.hanno.hpengine.graphics.renderer.extensions


import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.BindlessTextures
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.constants.BlendMode
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.vertexbuffer.draw
import de.hanno.hpengine.ressources.FileBasedCodeSource
import org.joml.Vector3f

class DirectionalLightSecondPassExtension(
    private val config: Config,
    private val programManager: ProgramManager,
    private val textureManager: OpenGLTextureManager,
    private val gpuContext: GpuContext,
    private val deferredRenderingBuffer: DeferredRenderingBuffer
) : DeferredRenderExtension {
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
//             TODO: Do i need this?
//            GL32.glFramebufferTexture(
//                GL30.GL_FRAMEBUFFER,
//                GL30.GL_DEPTH_ATTACHMENT,
//                deferredRenderingBuffer.depthBufferTexture,
//                0
//            )
            gpuContext.clearColor(0f, 0f, 0f, 0f)
            gpuContext.clearColorBuffer()

            profiled("Activate DeferredRenderingBuffer textures") {
                gpuContext.bindTexture(0, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
                gpuContext.bindTexture(1, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
                gpuContext.bindTexture(2, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
                gpuContext.bindTexture(3, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
                gpuContext.bindTexture(4, TextureTarget.TEXTURE_CUBE_MAP, textureManager.cubeMap.id)
                gpuContext.bindTexture(6, TextureTarget.TEXTURE_2D, renderState.directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId })
                gpuContext.bindTexture(7, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
                if (!gpuContext.isSupported(BindlessTextures)) {
                    gpuContext.bindTexture(
                        8,
                        TextureTarget.TEXTURE_2D,
                        renderState.directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId }
                    )
                }
                gpuContext.bindTexture(
                    9,
                    TextureTarget.TEXTURE_2D,
                    deferredRenderingBuffer.halfScreenBuffer.getRenderedTexture(2)
                )
                gpuContext.bindTexture(
                    10,
                    TextureTarget.TEXTURE_2D,
                    deferredRenderingBuffer.gBuffer.getRenderedTexture(4)
                )
            }

            secondPassDirectionalProgram.use()
            val camTranslation = Vector3f()
            secondPassDirectionalProgram.setUniform(
                "eyePosition",
                renderState.camera.getTranslation(camTranslation)
            )
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