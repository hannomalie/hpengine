package de.hanno.hpengine.graphics.renderer.extensions


import de.hanno.hpengine.artemis.EntitiesStateHolder
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.Access
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.PointLightStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.stopwatch.GPUProfiler

context(GPUProfiler, GraphicsApi)
class PointLightSecondPassExtension(
    private val graphicsApi: GraphicsApi,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val config: Config,
    programManager: ProgramManager,
    private val pointLightStateHolder: PointLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
) : DeferredRenderExtension {

    private val secondPassPointComputeProgram =
        programManager.getComputeProgram(config.EngineAsset("shaders/second_pass_point_trivial_compute.glsl"))

    override fun renderSecondPassFullScreen(renderState: RenderState) {
        if (renderState[pointLightStateHolder.lightState].pointLights.isEmpty()) {
            return
        }
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        profiled("Seconds pass PointLights") {

            val camera = renderState[primaryCameraStateHolder.camera]
            val viewMatrix = camera.viewMatrixAsBuffer
            val projectionMatrix = camera.projectionMatrixAsBuffer

            graphicsApi.bindTexture(0, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
            graphicsApi.bindTexture(1, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            graphicsApi.bindTexture(2, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
            graphicsApi.bindTexture(3, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            graphicsApi.bindTexture(4, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            graphicsApi.bindTexture(5, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
            renderState[pointLightStateHolder.lightState].pointLightShadowMapStrategy.bindTextures()
            graphicsApi.bindImageTexture(
                4,
                deferredRenderingBuffer.laBuffer.textures[0],
                0,
                false,
                0,
                Access.ReadWrite,
            )
            secondPassPointComputeProgram.use()
            secondPassPointComputeProgram.setUniform("pointLightCount",
                renderState[pointLightStateHolder.lightState].pointLights.size)
            secondPassPointComputeProgram.setUniform("screenWidth", config.width.toFloat())
            secondPassPointComputeProgram.setUniform("screenHeight", config.height.toFloat())
            secondPassPointComputeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            secondPassPointComputeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            secondPassPointComputeProgram.setUniform(
                "maxPointLightShadowmaps",
                PointLightSystem.MAX_POINTLIGHT_SHADOWMAPS
            )
            secondPassPointComputeProgram.bindShaderStorageBuffer(1, entitiesState.materialBuffer)
            secondPassPointComputeProgram.bindShaderStorageBuffer(2,
                renderState[pointLightStateHolder.lightState].pointLightBuffer)
            secondPassPointComputeProgram.dispatchCompute(
                config.width / 16,
                config.height / 16,
                1
            )
        }
    }
}