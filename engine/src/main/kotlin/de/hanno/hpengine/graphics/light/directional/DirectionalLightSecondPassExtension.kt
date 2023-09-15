package de.hanno.hpengine.graphics.light.directional

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.vertex.QuadVertexBuffer
import de.hanno.hpengine.graphics.buffer.vertex.draw
import de.hanno.hpengine.graphics.constants.BlendMode
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.feature.BindlessTextures
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.ressources.FileBasedCodeSource
import org.joml.Vector3f
import org.koin.core.annotation.Single
import struktgen.api.forIndex

@Single(binds = [DirectionalLightSecondPassExtension::class, DeferredRenderExtension::class])
class DirectionalLightSecondPassExtension(
    private val config: Config,
    private val programManager: ProgramManager,
    private val textureManager: OpenGLTextureManager,
    private val graphicsApi: GraphicsApi,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
) : DeferredRenderExtension {
    // TODO: Remove all the creations of fullscreenbuffer, so that it is shared and bound once?
    private val fullscreenBuffer = QuadVertexBuffer(graphicsApi)
    private val secondPassDirectionalProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/second_pass_directional_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/second_pass_directional_fragment.glsl"))
    )

    override fun renderSecondPassFullScreen(renderState: RenderState): Unit = graphicsApi.run {
        profiled("Directional light") {

            val directionalLightState = renderState[directionalLightStateHolder.lightState]

            profiled("Set state") {
                graphicsApi.depthMask = false
                graphicsApi.depthTest = false
                graphicsApi.blend = true
                graphicsApi.blendEquation = BlendMode.FUNC_ADD
                graphicsApi.blendFunc(BlendMode.Factor.ONE, BlendMode.Factor.ONE)
                graphicsApi.clearColor(0f, 0f, 0f, 0f)
                graphicsApi.clearColorBuffer()
            }

            profiled("Activate DeferredRenderingBuffer textures") {

                graphicsApi.bindTexture(0, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
                graphicsApi.bindTexture(1, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
                graphicsApi.bindTexture(2, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
                graphicsApi.bindTexture(3, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
                graphicsApi.bindTexture(4, TextureTarget.TEXTURE_CUBE_MAP, textureManager.cubeMap.id)

                val directionalShadowMap = directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId }
                if (directionalShadowMap > -1) {
                    graphicsApi.bindTexture(6, TextureTarget.TEXTURE_2D, directionalShadowMap)
                }
                graphicsApi.bindTexture(7, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
                if (!graphicsApi.isSupported(BindlessTextures)) {
                    graphicsApi.bindTexture(
                        8,
                        TextureTarget.TEXTURE_2D,
                        directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId }
                    )
                }
                graphicsApi.bindTexture(
                    9,
                    TextureTarget.TEXTURE_2D,
                    deferredRenderingBuffer.halfScreenBuffer.getRenderedTexture(2)
                )
                graphicsApi.bindTexture(
                    10,
                    TextureTarget.TEXTURE_2D,
                    deferredRenderingBuffer.gBuffer.getRenderedTexture(4)
                )
            }

            profiled("set shader input") {
                val camera = renderState[primaryCameraStateHolder.camera]
                secondPassDirectionalProgram.use()
                val camTranslation = Vector3f()
                secondPassDirectionalProgram.setUniform(
                    "eyePosition",
                    camera.getTranslation(camTranslation)
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
                secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
                secondPassDirectionalProgram.setUniformAsMatrix4(
                    "projectionMatrix",
                    camera.projectionMatrixAsBuffer
                )
                secondPassDirectionalProgram.bindShaderStorageBuffer(2, directionalLightState)
            }
            profiled("Draw fullscreen buffer") {
                fullscreenBuffer.draw()
            }
        }
    }
}