package de.hanno.hpengine.graphics.renderer.forward

import InternalTextureFormat
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.*
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.renderer.pipelines.DirectPipeline
import de.hanno.hpengine.graphics.rendertarget.RenderTarget2D
import de.hanno.hpengine.graphics.rendertarget.SharedDepthBuffer
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.state.StateRef
import de.hanno.hpengine.graphics.texture.TextureDimension2D
import de.hanno.hpengine.graphics.texture.UploadState
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.skybox.SkyBoxStateHolder
import de.hanno.hpengine.toCount
import org.joml.Vector4f
import org.koin.core.annotation.Single

@Single(binds = [RenderSystem::class, PrimaryRenderer::class])
class VisibilityRenderer(
    private val graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
    private val renderTarget: RenderTarget2D,
    private val programManager: ProgramManager,
    private val config: Config,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val materialSystem: MaterialSystem,
    private val skyBoxStateHolder: SkyBoxStateHolder,
    private val sharedDepthBuffer: SharedDepthBuffer,
    private val window: Window,
): PrimaryRenderer {

    private val frontBuffer = window.frontBuffer

    private val visibilityTexture = graphicsApi.Texture2D(
        TextureDimension2D(config.width, config.height),
        TextureTarget.TEXTURE_2D,
        InternalTextureFormat.RGBA16F,
        TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST),
        WrapMode.ClampToEdge,
        UploadState.Uploaded
    )
    private val visibilityRenderTarget = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(sharedDepthBuffer.depthBuffer),
        config.width, config.height,
        listOf(visibilityTexture),
        "Visibility",
        Vector4f(0f)
    )
    override val finalOutput = ForwardFinalOutput(renderTarget.textures.first(), 0, this)

    val simpleColorProgramStatic = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/visibility/visibility_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        StaticDefaultUniforms(graphicsApi)
    )

    val simpleColorProgramAnimated = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/visibility/visibility_fragment.glsl").toCodeSource(),
        null,
        Defines(Define("ANIMATED", true)),
        AnimatedDefaultUniforms(graphicsApi)
    )

    val resolveComputeProgram = programManager.getComputeProgram(
        config.EngineAsset("shaders/visibility/resolve_compute.glsl")
    )

    private val staticDirectPipeline: StateRef<DirectPipeline> = renderStateContext.renderState.registerState {
        object: DirectPipeline(graphicsApi, config, simpleColorProgramStatic, entitiesStateHolder, entityBuffer, primaryCameraStateHolder, defaultBatchesSystem, materialSystem) {
            override fun RenderState.extractRenderBatches(camera: Camera) = this[defaultBatchesSystem.renderBatchesStatic]
        }
    }
    private val animatedDirectPipeline: StateRef<DirectPipeline> = renderStateContext.renderState.registerState {
        object: DirectPipeline(graphicsApi, config, simpleColorProgramAnimated,entitiesStateHolder, entityBuffer, primaryCameraStateHolder, defaultBatchesSystem, materialSystem) {
            override fun RenderState.extractRenderBatches(camera: Camera) = this[defaultBatchesSystem.renderBatchesAnimated]

            override fun RenderState.selectGeometryBuffer() = this[entitiesStateHolder.entitiesState].geometryBufferAnimated
        }
    }

    override fun update(deltaSeconds: Float) {
        val currentWriteState = renderStateContext.renderState.currentWriteState

        currentWriteState[staticDirectPipeline].prepare(currentWriteState)
        currentWriteState[animatedDirectPipeline].prepare(currentWriteState)
    }

    override fun render(renderState: RenderState): Unit = graphicsApi.run {
        cullFace = true
        depthMask = true
        depthTest = true
        depthFunc = DepthFunc.LEQUAL
        blend = false

        visibilityRenderTarget.use(true)
        profiled("MainPipeline") {
            renderState[staticDirectPipeline].draw(renderState)
            renderState[animatedDirectPipeline].draw(renderState)
        }

        profiled("Resolve") {
            resolveComputeProgram.use()
            resolveComputeProgram.setUniform("width", renderTarget.width)
            resolveComputeProgram.setUniform("height", renderTarget.height)

            graphicsApi.bindTexture(0, visibilityTexture)
            graphicsApi.bindTexture(2, graphicsApi.textureArray)
            graphicsApi.bindImageTexture(3, renderTarget.renderedTexture, 0, false, 0, Access.ReadWrite, renderTarget.textures.first().internalFormat)

            resolveComputeProgram.bindShaderStorageBuffer(1, renderState[materialSystem.materialBuffer])
            resolveComputeProgram.bindShaderStorageBuffer(2, renderState[entityBuffer.entitiesBuffer])
            resolveComputeProgram.bindShaderStorageBuffer(3, renderState[entitiesStateHolder.entitiesState].geometryBufferStatic.vertexStructArray)
            resolveComputeProgram.dispatchCompute(renderTarget.width.toCount() / 4, renderTarget.height.toCount() / 4, 1.toCount())

        }
    }
}
