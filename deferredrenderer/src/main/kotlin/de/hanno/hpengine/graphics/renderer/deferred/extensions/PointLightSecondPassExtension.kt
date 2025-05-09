package de.hanno.hpengine.graphics.renderer.deferred.extensions

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.Access
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.light.point.MAX_POINTLIGHT_SHADOWMAPS
import de.hanno.hpengine.graphics.light.point.PointLightStateHolder
import de.hanno.hpengine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.toCount
import org.koin.core.annotation.Single

@Single(binds = [DeferredRenderExtension::class])
class PointLightSecondPassExtension(
    private val graphicsApi: GraphicsApi,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val config: Config,
    programManager: ProgramManager,
    private val pointLightStateHolder: PointLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val pointLightSystem: PointLightSystem,
    private val materialSystem: MaterialSystem,
) : DeferredRenderExtension {
    private val shadowMapStrategy = pointLightSystem.shadowMapStrategy

    private val secondPassPointComputeProgram =
        programManager.getComputeProgram(config.EngineAsset("shaders/second_pass_point_trivial_compute.glsl"))

    override fun renderSecondPassFullScreen(renderState: RenderState): Unit = graphicsApi.run {
        if (renderState[pointLightStateHolder.lightState].pointLightCount == 0) {
            return
        }

        // TODO: This doesn't work, find out why
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        profiled("Seconds pass PointLights") {

            val camera = renderState[primaryCameraStateHolder.camera]
            val viewMatrix = camera.viewMatrixBuffer
            val projectionMatrix = camera.projectionMatrixBuffer

            graphicsApi.bindTexture(0, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
            graphicsApi.bindTexture(1, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            graphicsApi.bindTexture(2, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
            graphicsApi.bindTexture(3, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            graphicsApi.bindTexture(4, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            graphicsApi.bindTexture(5, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
            shadowMapStrategy.bindTextures()
            graphicsApi.bindImageTexture(
                4,
                deferredRenderingBuffer.laBuffer.textures[0],
                0,
                false,
                0,
                Access.ReadWrite,
            )
            secondPassPointComputeProgram.use()
            secondPassPointComputeProgram.setUniform(
                "pointLightCount",
                renderState[pointLightStateHolder.lightState].pointLightCount
            )
            secondPassPointComputeProgram.setUniform("screenWidth", config.width.toFloat())
            secondPassPointComputeProgram.setUniform("screenHeight", config.height.toFloat())
            secondPassPointComputeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            secondPassPointComputeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            secondPassPointComputeProgram.setUniform(
                "maxPointLightShadowmaps",
                MAX_POINTLIGHT_SHADOWMAPS
            )
            secondPassPointComputeProgram.bindShaderStorageBuffer(1, renderState[materialSystem.materialBuffer])
            secondPassPointComputeProgram.bindShaderStorageBuffer(
                2,
                renderState[pointLightStateHolder.lightState].pointLightBuffer
            )
            secondPassPointComputeProgram.dispatchCompute(
                config.width.toCount() / 16,
                config.height.toCount() / 16,
                1.toCount()
            )
        }
    }
}