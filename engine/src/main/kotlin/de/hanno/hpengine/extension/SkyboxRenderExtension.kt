package de.hanno.hpengine.extension

import com.artemis.World
import de.hanno.hpengine.artemis.EntitiesStateHolder

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.RenderStateContext
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42

context(GpuContext, RenderStateContext)
class SkyboxRenderExtension(
    private val config: Config,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val programManager: ProgramManager,
    private val textureManager: OpenGLTextureManager,
    private val entitiesStateHolder: EntitiesStateHolder,
) : DeferredRenderExtension {

    init {
        bindTexture(
            6,
            TextureTarget.TEXTURE_CUBE_MAP,
            textureManager.cubeMap.id
        )
    }

    private val secondPassReflectionProgram = programManager.getComputeProgram(
        config.EngineAsset("shaders/second_pass_skybox_reflection.glsl")
    )
    val skyBoxTexture = renderState.registerState {
        textureManager.cubeMap.id
    }

    override fun extract(renderState: RenderState, world: World) {
//         TODO: Reimplement
//        renderState[skyBoxTexture].value = it.id
    }

    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        bindTexture(0, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
        bindTexture(1, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
        bindTexture(2, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
        bindTexture(3, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
        bindTexture(4, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
        bindTexture(5, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
        // TODO: Reimplement with artemis system extraction
        bindTexture(6, TextureTarget.TEXTURE_CUBE_MAP, renderState[skyBoxTexture])
        bindTexture(6, TextureTarget.TEXTURE_CUBE_MAP, textureManager.cubeMap.id)
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
        secondPassReflectionProgram.bindShaderStorageBuffer(1, renderState[entitiesStateHolder.entitiesState].materialBuffer)
        secondPassReflectionProgram.dispatchCompute(
            config.width / 16,
            config.height / 16,
            1
        )
    }
}