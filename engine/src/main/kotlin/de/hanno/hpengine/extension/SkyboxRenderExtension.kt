package de.hanno.hpengine.extension

import InternalTextureFormat.RGBA16F
import com.artemis.World
import de.hanno.hpengine.artemis.EntitiesStateHolder
import de.hanno.hpengine.artemis.PrimaryCameraStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.Access.ReadWrite
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderStateContext
import de.hanno.hpengine.graphics.renderer.constants.TextureTarget
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager

context(GraphicsApi, RenderStateContext)
class SkyboxRenderExtension(
    private val config: Config,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val programManager: ProgramManager,
    private val textureManager: OpenGLTextureManager,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
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

    override fun renderSecondPassFullScreen(renderState: RenderState) {
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
        bindImageTexture(
            4,
            deferredRenderingBuffer.reflectionBuffer.renderedTextures[0],
            0,
            false,
            0,
            ReadWrite,
            RGBA16F
        )
        bindImageTexture(
            7,
            deferredRenderingBuffer.reflectionBuffer.renderedTextures[1],
            0,
            false,
            0,
            ReadWrite,
            RGBA16F
        )
        val camera = renderState[primaryCameraStateHolder.camera]
        secondPassReflectionProgram.use()
        secondPassReflectionProgram.setUniform("screenWidth", config.width.toFloat())
        secondPassReflectionProgram.setUniform("screenHeight", config.height.toFloat())
        secondPassReflectionProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixAsBuffer)
        secondPassReflectionProgram.setUniformAsMatrix4(
            "projectionMatrix",
            camera.projectionMatrixAsBuffer
        )
        secondPassReflectionProgram.bindShaderStorageBuffer(1, renderState[entitiesStateHolder.entitiesState].materialBuffer)
        secondPassReflectionProgram.dispatchCompute(
            config.width / 16,
            config.height / 16,
            1
        )
    }
}