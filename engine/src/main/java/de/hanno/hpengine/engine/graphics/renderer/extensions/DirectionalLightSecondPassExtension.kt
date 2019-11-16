package de.hanno.hpengine.engine.graphics.renderer.extensions

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.BindlessTextures
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.draw
import de.hanno.hpengine.engine.scene.EnvironmentProbeManager
import org.joml.Vector3f
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL32
import java.io.File

class DirectionalLightSecondPassExtension(val engineContext: EngineContext<OpenGl>): RenderExtension<OpenGl> {
    private val secondPassDirectionalProgram = engineContext.programManager.getProgram(getShaderSource(File(Shader.directory + "second_pass_directional_vertex.glsl")), getShaderSource(File(Shader.directory + "second_pass_directional_fragment.glsl")))

    private val gpuContext = engineContext.gpuContext
    private val deferredRenderingBuffer = engineContext.deferredRenderingBuffer

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

        profiled("Directional light") {

            val viewMatrix = renderState.camera.viewMatrixAsBuffer
            val projectionMatrix = renderState.camera.projectionMatrixAsBuffer

            gpuContext.depthMask(false)
            gpuContext.disable(GlCap.DEPTH_TEST)
            gpuContext.enable(GlCap.BLEND)
            gpuContext.blendEquation(BlendMode.FUNC_ADD)
            gpuContext.blendFunc(BlendMode.Factor.ONE, BlendMode.Factor.ONE)

            deferredRenderingBuffer.lightAccumulationBuffer.use(gpuContext, true)
//                    GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, gBuffer.depthBufferTexture)
            GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, deferredRenderingBuffer.depthBufferTexture, 0)
            gpuContext.clearColor(0f, 0f, 0f, 0f)
            gpuContext.clearColorBuffer()

            profiled("Activate DeferredRenderingBuffer textures") {
                gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
                gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
                gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
                gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
                gpuContext.bindTexture(4, GlTextureTarget.TEXTURE_CUBE_MAP, engineContext.textureManager.cubeMap!!.id)
                gpuContext.bindTexture(6, GlTextureTarget.TEXTURE_2D, renderState.directionalLightState.shadowMapId)
                gpuContext.bindTexture(7, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
                if(renderState.environmentProbesState.environmapsArray3Id > 0) {
                    gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_CUBE_MAP_ARRAY, renderState.environmentProbesState.environmapsArray3Id)
                }
                if(!gpuContext.isSupported(BindlessTextures)) {
                    gpuContext.bindTexture(8, GlTextureTarget.TEXTURE_2D, renderState.directionalLightState.shadowMapId)
                }
            }

            secondPassDirectionalProgram.use()
            val camTranslation = Vector3f()
            secondPassDirectionalProgram.setUniform("eyePosition", renderState.camera.getTranslation(camTranslation))
            secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", engineContext.config.effects.ambientocclusionRadius)
            secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", engineContext.config.effects.ambientocclusionTotalStrength)
            secondPassDirectionalProgram.setUniform("screenWidth", engineContext.config.width.toFloat())
            secondPassDirectionalProgram.setUniform("screenHeight", engineContext.config.height.toFloat())
            secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix)
            secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix)
            secondPassDirectionalProgram.bindShaderStorageBuffer(2, renderState.directionalLightBuffer)
            EnvironmentProbeManager.bindEnvironmentProbePositions(secondPassDirectionalProgram, renderState.environmentProbesState)
            profiled("Draw fullscreen buffer") {
                gpuContext.fullscreenBuffer.draw()
            }

        }
    }
}