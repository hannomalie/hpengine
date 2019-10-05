package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.state.RenderState
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42

class PointLightSecondPassExtension(val engineContext: EngineContext<OpenGl>): RenderExtension<OpenGl> {
    private val gpuContext = engineContext.gpuContext
    private val deferredRenderingBuffer = engineContext.deferredRenderingBuffer

    private val secondPassPointComputeProgram = engineContext.programManager.getComputeProgram("second_pass_point_trivial_compute.glsl")

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        if (renderState.lightState.pointLights.isEmpty()) {
            return
        }
        profiled("Seconds pass PointLights") {

            val viewMatrix = renderState.camera.viewMatrixAsBuffer
            val projectionMatrix = renderState.camera.projectionMatrixAsBuffer

            gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
            gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
            gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            gpuContext.bindTexture(4, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            gpuContext.bindTexture(5, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
            renderState.lightState.pointLightShadowMapStrategy.bindTextures()
            // TODO: Add glbindimagetexture to openglcontext class
            GL42.glBindImageTexture(4, deferredRenderingBuffer.lightAccumulationMapOneId, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA16F)
            secondPassPointComputeProgram.use()
            secondPassPointComputeProgram.setUniform("pointLightCount", renderState.lightState.pointLights.size)
            secondPassPointComputeProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
            secondPassPointComputeProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
            secondPassPointComputeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            secondPassPointComputeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            secondPassPointComputeProgram.setUniform("maxPointLightShadowmaps", PointLightSystem.MAX_POINTLIGHT_SHADOWMAPS)
            secondPassPointComputeProgram.bindShaderStorageBuffer(1, renderState.materialBuffer)
            secondPassPointComputeProgram.bindShaderStorageBuffer(2, renderState.lightState.pointLightBuffer)
            secondPassPointComputeProgram.dispatchCompute(engineContext.config.width / 16, engineContext.config.height / 16, 1)
        }
    }
}