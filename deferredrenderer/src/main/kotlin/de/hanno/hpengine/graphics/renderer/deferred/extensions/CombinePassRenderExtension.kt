package de.hanno.hpengine.graphics.renderer.deferred.extensions

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.vertex.QuadVertexBuffer
import de.hanno.hpengine.graphics.constants.Capability
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.envprobe.EnvironmentProbesStateHolder
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.rendertarget.RenderTarget2D
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource

class CombinePassRenderExtension(private val config: Config,
                                 private val programManager: ProgramManager,
                                 private val textureManager: TextureManager,
                                 private val graphicsApi: GraphicsApi,
                                 private val deferredRenderingBuffer: DeferredRenderingBuffer,
                                 private val environmentProbesStateHolder: EnvironmentProbesStateHolder,
                                 private val primaryCameraStateHolder: PrimaryCameraStateHolder,
): DeferredRenderExtension {

    private val fullscreenBuffer = graphicsApi.run { QuadVertexBuffer(graphicsApi) }
    private val combineProgram = programManager.getProgram(
        config.EngineAsset("shaders/combine_pass_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/combine_pass_fragment.glsl").toCodeSource(),
        Uniforms.Empty,
        Defines()
    )

    fun renderCombinePass(
        state: RenderState,
        renderTarget: RenderTarget2D = deferredRenderingBuffer.finalBuffer
    ) = graphicsApi.run {
        val camera = state[primaryCameraStateHolder.camera]

        if(!config.effects.isAutoExposureEnabled) {
            deferredRenderingBuffer.exposureBuffer.buffer.putFloat(0, camera.exposure)
        }
        profiled("Combine pass") {
            renderTarget.use(false)

            generateMipMaps(renderTarget.textures[0])

            combineProgram.use()
            combineProgram.setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixBuffer)
            combineProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixBuffer)
            combineProgram.setUniform("screenWidth", config.width.toFloat())
            combineProgram.setUniform("screenHeight", config.height.toFloat())
            combineProgram.setUniform("camPosition", camera.getPosition())
            combineProgram.setUniform("ambientColor", config.effects.ambientLight)
            combineProgram.setUniform("useAmbientOcclusion", config.quality.isUseAmbientOcclusion)
            combineProgram.setUniform("worldExposure", camera.exposure)
            combineProgram.setUniform("AUTO_EXPOSURE_ENABLED", config.effects.isAutoExposureEnabled)
            combineProgram.setUniform("fullScreenMipmapCount", deferredRenderingBuffer.fullScreenMipmapCount)
            combineProgram.setUniform("activeProbeCount", state[environmentProbesStateHolder.environmentProbesState].activeProbeCount)
            combineProgram.bindShaderStorageBuffer(0, deferredRenderingBuffer.exposureBuffer)

            disable(Capability.DEPTH_TEST)
            disable(Capability.BLEND)
            depthMask = false
            disable(Capability.CULL_FACE)

            bindTexture(0, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
            bindTexture(1, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            bindTexture(2, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationBuffer.getRenderedTexture(1))
            bindTexture(3, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            bindTexture(4, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
            bindTexture(5, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            bindTexture(6, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.forwardBuffer.getRenderedTexture(0))
            bindTexture(7, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.forwardBuffer.getRenderedTexture(1))
            //			getGpuContext().bindTexture(7, TEXTURE_CUBE_MAP_ARRAY, renderState.getEnvironmentProbesState().getEnvironmapsArray0Id());
            bindTexture(8, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.reflectionMap)
            bindTexture(9, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.refractedMap)
            bindTexture(11, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.ambientOcclusionScatteringMap)
            bindTexture(14, TextureTarget.TEXTURE_CUBE_MAP, textureManager.cubeMap.id)
            bindTexture(15, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.halfScreenBuffer.renderedTextures[1])

            combineProgram.bind()
            fullscreenBuffer.draw(indexBuffer = null)

        }
    }
}