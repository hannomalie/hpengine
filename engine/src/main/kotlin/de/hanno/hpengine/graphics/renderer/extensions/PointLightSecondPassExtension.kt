package de.hanno.hpengine.graphics.renderer.extensions


import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.PointLightStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42

class PointLightSecondPassExtension(
    private val gpuContext: GpuContext,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val config: Config,
    programManager: ProgramManager,
    private val pointLightStateHolder: PointLightStateHolder,
) : DeferredRenderExtension {

    private val secondPassPointComputeProgram =
        programManager.getComputeProgram(config.EngineAsset("shaders/second_pass_point_trivial_compute.glsl"))

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        if (renderState[pointLightStateHolder.lightState].pointLights.isEmpty()) {
            return
        }
        profiled("Seconds pass PointLights") {

            val viewMatrix = renderState.camera.viewMatrixAsBuffer
            val projectionMatrix = renderState.camera.projectionMatrixAsBuffer

            gpuContext.bindTexture(0, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
            gpuContext.bindTexture(1, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            gpuContext.bindTexture(2, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
            gpuContext.bindTexture(3, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            gpuContext.bindTexture(4, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            gpuContext.bindTexture(5, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
            renderState[pointLightStateHolder.lightState].pointLightShadowMapStrategy.bindTextures()
            // TODO: Add glbindimagetexture to openglcontext class
            GL42.glBindImageTexture(
                4,
                deferredRenderingBuffer.lightAccumulationMapOneId,
                0,
                false,
                0,
                GL15.GL_READ_WRITE,
                GL30.GL_RGBA16F
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
            secondPassPointComputeProgram.bindShaderStorageBuffer(1, renderState.materialBuffer)
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