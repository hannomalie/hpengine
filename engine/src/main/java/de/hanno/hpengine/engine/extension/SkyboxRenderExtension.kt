package de.hanno.hpengine.engine.extension

import com.artemis.World
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.TextureManager
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42

class SkyboxRenderExtension(
    val config: Config,
    val gpuContext: GpuContext<OpenGl>,
    val deferredRenderingBuffer: DeferredRenderingBuffer,
    val programManager: ProgramManager<OpenGl>,
    val textureManager: TextureManager,
    val renderStateManager: RenderStateManager
) : DeferredRenderExtension<OpenGl> {

    init {
        gpuContext.bindTexture(
            6,
            GlTextureTarget.TEXTURE_CUBE_MAP,
            textureManager.cubeMap.id
        )
    }

    private val secondPassReflectionProgram = programManager.getComputeProgram(
        config.EngineAsset("shaders/second_pass_skybox_reflection.glsl")
    )
    val skyBoxTexture = renderStateManager.renderState.registerState {
        IntStruct().apply {
            value = textureManager.cubeMap.id
        }
    }

    override fun extract(renderState: RenderState, world: World) {
//         TODO: Reimplement
//        renderState[skyBoxTexture].value = it.id
    }

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
        gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
        gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
        gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
        gpuContext.bindTexture(4, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
        gpuContext.bindTexture(5, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
        // TODO: Reimplement with artemis system extraction
//            gpuContext.bindTexture(6, GlTextureTarget.TEXTURE_CUBE_MAP, renderState[skyBoxTexture].value)
        gpuContext.bindTexture(6, GlTextureTarget.TEXTURE_CUBE_MAP, textureManager.cubeMap.id)
        // TODO: Add glbindimagetexture to openglcontext class
        GL42.glBindImageTexture(
            4,
            deferredRenderingBuffer.reflectionBuffer.renderedTextures[0],
            0,
            false,
            0,
            GL15.GL_READ_WRITE,
            GL30.GL_RGBA16F
        )
        GL42.glBindImageTexture(
            7,
            deferredRenderingBuffer.reflectionBuffer.renderedTextures[1],
            0,
            false,
            0,
            GL15.GL_READ_WRITE,
            GL30.GL_RGBA16F
        )
        secondPassReflectionProgram.use()
        secondPassReflectionProgram.setUniform("screenWidth", config.width.toFloat())
        secondPassReflectionProgram.setUniform("screenHeight", config.height.toFloat())
        secondPassReflectionProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
        secondPassReflectionProgram.setUniformAsMatrix4(
            "projectionMatrix",
            renderState.camera.projectionMatrixAsBuffer
        )
        secondPassReflectionProgram.bindShaderStorageBuffer(1, renderState.materialBuffer)
        secondPassReflectionProgram.dispatchCompute(
            config.width / 16,
            config.height / 16,
            1
        )
    }
}