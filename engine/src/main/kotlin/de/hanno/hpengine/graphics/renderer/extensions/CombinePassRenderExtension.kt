package de.hanno.hpengine.graphics.renderer.extensions

import de.hanno.hpengine.artemis.EnvironmentProbesStateHolder
import de.hanno.hpengine.artemis.PrimaryCameraStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.constants.Capability
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.rendertarget.BackBufferRenderTarget
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLTexture2D
import de.hanno.hpengine.graphics.vertexbuffer.draw
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.stopwatch.GPUProfiler

context(GPUProfiler)
class CombinePassRenderExtension(private val config: Config,
                                 private val programManager: ProgramManager,
                                 private val textureManager: TextureManager,
                                 private val gpuContext: GpuContext,
                                 private val deferredRenderingBuffer: DeferredRenderingBuffer,
                                 private val environmentProbesStateHolder: EnvironmentProbesStateHolder,
                                 private val primaryCameraStateHolder: PrimaryCameraStateHolder,
): DeferredRenderExtension {

    private val combineProgram = programManager.getProgram(
        config.EngineAsset("shaders/combine_pass_vertex.glsl").toCodeSource(),
        config.EngineAsset("shaders/combine_pass_fragment.glsl").toCodeSource(),
        Uniforms.Empty,
        Defines()
    )

    fun renderCombinePass(state: RenderState, renderTarget: BackBufferRenderTarget<OpenGLTexture2D> = deferredRenderingBuffer.finalBuffer) {
        val camera = state[primaryCameraStateHolder.camera]

        if(!config.effects.isAutoExposureEnabled) {
            deferredRenderingBuffer.exposureBuffer.buffer.putFloat(0, camera.exposure)
        }
        profiled("Combine pass") {
            renderTarget.use(false)

            textureManager.generateMipMaps(TextureTarget.TEXTURE_2D, renderTarget.getRenderedTexture(0))

            combineProgram.use()
            combineProgram.setUniformAsMatrix4("projectionMatrix", camera.projectionMatrixAsBuffer)
            combineProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
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

            gpuContext.disable(Capability.DEPTH_TEST)
            gpuContext.disable(Capability.BLEND)
            gpuContext.depthMask = false
            gpuContext.disable(Capability.CULL_FACE)

            gpuContext.bindTexture(0, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
            gpuContext.bindTexture(1, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            gpuContext.bindTexture(2, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationBuffer.getRenderedTexture(1))
            gpuContext.bindTexture(3, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            gpuContext.bindTexture(4, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
            gpuContext.bindTexture(5, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            gpuContext.bindTexture(6, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.forwardBuffer.getRenderedTexture(0))
            gpuContext.bindTexture(7, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.forwardBuffer.getRenderedTexture(1))
            //			getGpuContext().bindTexture(7, TEXTURE_CUBE_MAP_ARRAY, renderState.getEnvironmentProbesState().getEnvironmapsArray0Id());
            gpuContext.bindTexture(8, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.reflectionMap)
            gpuContext.bindTexture(9, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.refractedMap)
            gpuContext.bindTexture(11, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.ambientOcclusionScatteringMap)
            gpuContext.bindTexture(14, TextureTarget.TEXTURE_CUBE_MAP, textureManager.cubeMap.id)
            gpuContext.bindTexture(15, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.halfScreenBuffer.renderedTextures[1])

            gpuContext.fullscreenBuffer.draw()

        }
    }
}