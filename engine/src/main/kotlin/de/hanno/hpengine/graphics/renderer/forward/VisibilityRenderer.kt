package de.hanno.hpengine.graphics.renderer.forward

import InternalTextureFormat
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.*
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.feature.BindlessTextures
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.light.point.CubeShadowMapStrategy
import de.hanno.hpengine.graphics.light.point.PointLightStateHolder
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
import de.hanno.hpengine.graphics.texture.TextureDescription.Texture2DDescription
import de.hanno.hpengine.graphics.texture.TextureDimension2D
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.skybox.SkyBoxStateHolder
import de.hanno.hpengine.toCount
import org.joml.Vector4f
import org.koin.core.annotation.Single
import struktgen.api.forIndex

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
    private val pointLightStateHolder: PointLightStateHolder,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val sharedDepthBuffer: SharedDepthBuffer,
    private val cubeShadowMapStrategy: CubeShadowMapStrategy,
) : PrimaryRenderer {
    init {
        require(graphicsApi.isSupported(BindlessTextures)) {
            "BindlessTextures not supported, visibility rendering impossible!"
        }
    }

    private val visibilityTexture = graphicsApi.Texture2D(
        Texture2DDescription(
            TextureDimension2D(config.width, config.height),
            InternalTextureFormat.RGBA16F,
            TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST),
            WrapMode.ClampToEdge
        )
    )
    private val normalTexture = graphicsApi.Texture2D(
        Texture2DDescription(
            TextureDimension2D(config.width, config.height),
            InternalTextureFormat.RGBA16F,
            TextureFilterConfig(MinFilter.NEAREST, MagFilter.NEAREST),
            WrapMode.ClampToEdge
        )
    )
    private val visibilityRenderTarget = graphicsApi.RenderTarget(
        graphicsApi.FrameBuffer(sharedDepthBuffer.depthBuffer),
        config.width, config.height,
        listOf(visibilityTexture, normalTexture),
        "Visibility",
        Vector4f(0f)
    )
    override val finalOutput = SimpleFinalOutput(renderTarget.textures.first(), 0, this)

    val visibilityProgramStatic = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/visibility/visibility_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        StaticDefaultUniforms(graphicsApi)
    )

    val visibilityProgramAnimated = programManager.getProgram(
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
        object : DirectPipeline(
            graphicsApi,
            config,
            visibilityProgramStatic,
            entitiesStateHolder,
            entityBuffer,
            primaryCameraStateHolder,
            defaultBatchesSystem,
            materialSystem
        ) {
            override fun RenderState.extractRenderBatches(camera: Camera) =
                this[defaultBatchesSystem.renderBatchesStatic]
        }
    }
    private val animatedDirectPipeline: StateRef<DirectPipeline> = renderStateContext.renderState.registerState {
        object : DirectPipeline(
            graphicsApi,
            config,
            visibilityProgramAnimated,
            entitiesStateHolder,
            entityBuffer,
            primaryCameraStateHolder,
            defaultBatchesSystem,
            materialSystem
        ) {
            override fun RenderState.extractRenderBatches(camera: Camera) =
                this[defaultBatchesSystem.renderBatchesAnimated]

            override fun RenderState.selectGeometryBuffer() =
                this[entitiesStateHolder.entitiesState].geometryBufferAnimated
        }
    }

    override fun update(deltaSeconds: Float) {
        val currentWriteState = renderStateContext.renderState.currentWriteState

        currentWriteState[staticDirectPipeline].prepare(currentWriteState)
        currentWriteState[animatedDirectPipeline].prepare(currentWriteState)
    }

    override fun render(renderState: RenderState): Unit = graphicsApi.run {
        val pointLightState = renderState[pointLightStateHolder.lightState]
        val directionalLightState = renderState[directionalLightStateHolder.lightState]

        cullFace = true // TODO: Make possible per batch
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
            resolveComputeProgram.setUniformAsMatrix4(
                "viewMatrix",
                renderState[primaryCameraStateHolder.camera].viewMatrixBuffer
            )
            resolveComputeProgram.setUniformAsMatrix4(
                "projectionMatrix",
                renderState[primaryCameraStateHolder.camera].projectionMatrixBuffer
            )

            graphicsApi.bindTexture(0, visibilityTexture)
            graphicsApi.bindTexture(1, sharedDepthBuffer.depthBuffer.texture)
            graphicsApi.bindTexture(3, normalTexture)
            graphicsApi.bindImageTexture(
                3,
                renderTarget.renderedTexture,
                0,
                false,
                0,
                Access.ReadWrite,
                renderTarget.textures.first().internalFormat
            )
            cubeShadowMapStrategy.bindTextures()
            if (!graphicsApi.isSupported(BindlessTextures)) {
                graphicsApi.bindTexture(
                    9,
                    TextureTarget.TEXTURE_2D,
                    directionalLightState.typedBuffer.forIndex(0) { it.shadowMapId }
                )
                graphicsApi.bindTexture(
                    10,
                    TextureTarget.TEXTURE_2D,
                    directionalLightState.typedBuffer.forIndex(0) { it.staticShadowMapId }
                )
            }

            resolveComputeProgram.bindShaderStorageBuffer(1, renderState[materialSystem.materialBuffer])
            resolveComputeProgram.bindShaderStorageBuffer(2, renderState[entityBuffer.entitiesBuffer])
            resolveComputeProgram.setUniform("pointLightCount", pointLightState.pointLightCount)
            resolveComputeProgram.bindShaderStorageBuffer(3, pointLightState.pointLightBuffer)
            resolveComputeProgram.bindShaderStorageBuffer(4, directionalLightState.gpuBuffer)
            resolveComputeProgram.dispatchCompute(
                renderTarget.width.toCount() / 4,
                renderTarget.height.toCount() / 4,
                1.toCount()
            )
        }
    }
}
