package de.hanno.hpengine.skybox

import InternalTextureFormat
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.Access
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.TextureTarget
import de.hanno.hpengine.graphics.envprobe.EnvironmentProbeSystem
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.toCount
import org.koin.core.annotation.Single

@Single(binds = [SkyboxRenderExtension::class, DeferredRenderExtension::class])
class SkyboxRenderExtension(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val programManager: ProgramManager,
    private val textureManager: OpenGLTextureManager,
    private val materialSystem: MaterialSystem,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val skyBoxStateHolder: SkyBoxStateHolder,
    private val probeSystem: EnvironmentProbeSystem,
) : DeferredRenderExtension {

    init {
        graphicsApi.bindTexture(
            6,
            TextureTarget.TEXTURE_CUBE_MAP,
            textureManager.cubeMap.id
        )
        val loadSomeSkyboxes = true
        if(loadSomeSkyboxes) {
            config.engineDir.apply {
                textureManager.getCubeMap(
                    "assets/textures/skybox/skybox2.jpg",
                    resolve("assets/textures/skybox/skybox2.jpg")
                )
                textureManager.getCubeMap(
                    "assets/textures/skybox/skybox3.jpg",
                    resolve("assets/textures/skybox/skybox3.jpg")
                )
//                TODO: This causes some opengl error when trying to load it
//                textureManager.getCubeMap(
//                    "assets/textures/skybox/skybox4.jpg",
//                    resolve("assets/textures/skybox/skybox4.jpg")
//                )
                textureManager.getCubeMap(
                    "assets/textures/skybox/skybox5.jpg",
                    resolve("assets/textures/skybox/skybox5.jpg")
                )
                textureManager.getCubeMap(
                    "assets/textures/skybox/skybox6.jpg",
                    resolve("assets/textures/skybox/skybox6.jpg")
                )
//                TODO: This causes some opengl error when trying to load it
//                textureManager.getCubeMap(
//                    "assets/textures/skybox/skybox7.jpg",
//                    resolve("assets/textures/skybox/skybox7.jpg")
//                )
            }
        }
    }

    private val secondPassReflectionProgram = programManager.getComputeProgram(
        config.EngineAsset("shaders/second_pass_skybox_reflection.glsl")
    )

    override fun renderSecondPassFullScreen(renderState: RenderState) = graphicsApi.run {
        bindTexture(0, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
        bindTexture(1, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
        bindTexture(2, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
        bindTexture(3, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
        bindTexture(4, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
        bindTexture(5, TextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
        val skyBoxMaterialIndex = renderState[skyBoxStateHolder.skyBoxMaterialIndex]
        val skyboxTexture = if(skyBoxMaterialIndex > -1) {
            materialSystem.materials[renderState[skyBoxStateHolder.skyBoxMaterialIndex]].maps[Material.MAP.ENVIRONMENT]!!
        } else {
            textureManager.cubeMap
        }
        bindTexture(6, TextureTarget.TEXTURE_CUBE_MAP, skyboxTexture.id)
        bindTexture(8, probeSystem.cubeMapArray)
        bindImageTexture(4, deferredRenderingBuffer.reflectionBuffer.renderedTextures[0], 0, false, 0, Access.ReadWrite, InternalTextureFormat.RGBA16F)
        bindImageTexture(7, deferredRenderingBuffer.reflectionBuffer.renderedTextures[1], 0, false, 0, Access.ReadWrite, InternalTextureFormat.RGBA16F)

        val camera = renderState[primaryCameraStateHolder.camera]
        secondPassReflectionProgram.use()
        secondPassReflectionProgram.setUniform("screenWidth", config.width.toFloat())
        secondPassReflectionProgram.setUniform("screenHeight", config.height.toFloat())
        secondPassReflectionProgram.setUniformAsMatrix4("viewMatrix", camera.viewMatrixBuffer)
        secondPassReflectionProgram.setUniformAsMatrix4(
            "projectionMatrix",
            camera.projectionMatrixBuffer
        )
        secondPassReflectionProgram.bindShaderStorageBuffer(1, renderState[materialSystem.materialBuffer])
        secondPassReflectionProgram.dispatchCompute(
            config.width.toCount() / 16,
            config.height.toCount() / 16,
            1.toCount(),
        )
    }
}