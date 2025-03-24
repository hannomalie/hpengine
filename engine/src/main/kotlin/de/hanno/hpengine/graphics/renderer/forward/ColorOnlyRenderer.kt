package de.hanno.hpengine.graphics.renderer.forward

import de.hanno.hpengine.Transform
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.cycle.CycleSystem
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.PrimaryRenderer
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.constants.DepthFunc
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.pipelines.DirectPipeline
import de.hanno.hpengine.graphics.renderer.pipelines.setCommonUniformValues
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.rendertarget.RenderTarget2D
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.shader.useAndBind
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.state.StateRef
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.graphics.texture.TextureUsageInfo
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.skybox.SkyBoxStateHolder
import de.hanno.hpengine.transform.isInside
import org.joml.Vector3f
import org.joml.Vector4f
import org.koin.core.annotation.Single
import org.koin.ksp.generated.module
import org.lwjgl.BufferUtils

@Single(binds = [RenderSystem::class, PrimaryRenderer::class])
class ColorOnlyRenderer(
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
    private val cycleSystem: CycleSystem,
    private val textureManager: TextureManager,
): PrimaryRenderer {

    private val lineRenderer = ColorOnlyLineRenderer(graphicsApi, programManager, primaryCameraStateHolder)
    override val finalOutput = SimpleFinalOutput(renderTarget.textures.first(), 0, this)

    val simpleColorProgramStatic = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/color_only/color_out_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        StaticDefaultUniforms(graphicsApi)
    )

    val simpleColorProgramAnimated = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/color_only/color_out_fragment.glsl").toCodeSource(),
        null,
        Defines(Define("ANIMATED", true)),
        AnimatedDefaultUniforms(graphicsApi)
    )

    val simpleColorProgramSkyBox = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/color_only/first_pass_skybox_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        StaticDefaultUniforms(graphicsApi)
    )

    private val staticDirectPipeline: StateRef<DirectPipeline> = renderStateContext.renderState.registerState {
        object: DirectPipeline(
            graphicsApi = graphicsApi,
            config = config,
            program = simpleColorProgramStatic,
            entitiesStateHolder = entitiesStateHolder,
            entityBuffer = entityBuffer,
            primaryCameraStateHolder = primaryCameraStateHolder,
            defaultBatchesSystem = defaultBatchesSystem,
            materialSystem = materialSystem,
            fallbackTexture = textureManager.defaultTexture,
        ) {
            override fun RenderState.extractRenderBatches(camera: Camera) = this[defaultBatchesSystem.renderBatchesStatic].filter {
                !it.hasOwnProgram && it.isVisibleForCamera
            }
        }
    }
    private val animatedDirectPipeline: StateRef<DirectPipeline> = renderStateContext.renderState.registerState {
        object: DirectPipeline(
            graphicsApi = graphicsApi,
            config = config,
            program = simpleColorProgramAnimated,
            entitiesStateHolder = entitiesStateHolder,
            entityBuffer = entityBuffer,
            primaryCameraStateHolder = primaryCameraStateHolder,
            defaultBatchesSystem = defaultBatchesSystem,
            materialSystem = materialSystem,
            fallbackTexture = textureManager.defaultTexture,
        ) {
            override fun RenderState.extractRenderBatches(camera: Camera) = this[defaultBatchesSystem.renderBatchesAnimated].filter {
                !it.hasOwnProgram && it.isVisibleForCamera
            }

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

        renderTarget.use(true)
        profiled("MainPipeline") {
            renderState[staticDirectPipeline].draw(renderState)
            renderState[animatedDirectPipeline].draw(renderState)

            val batch = renderState[skyBoxStateHolder.batch].underlying

            simpleColorProgramSkyBox.useAndBind(setUniforms = {
                setCommonUniformValues(
                    renderState,
                    renderState[entitiesStateHolder.entitiesState],
                    renderState[primaryCameraStateHolder.camera],
                    config, materialSystem, entityBuffer
                )
                simpleColorProgramSkyBox.setTextureUniforms(graphicsApi, null, batch.material)
            }, block = {
                renderState[entitiesStateHolder.entitiesState].geometryBufferStatic.draw(
                    batch.drawElementsIndirectCommand,
                    primitiveType = PrimitiveType.Triangles,
                    mode = if(config.debug.isDrawLines) RenderingMode.Lines else RenderingMode.Fill,
                    bindIndexBuffer = true,
                )
            })

            if(config.debug.drawBoundingVolumes) {
                lineRenderer.render(
                    renderState,
                    renderState[defaultBatchesSystem.renderBatchesStatic].flatMap {
                        listOf(
                            it.meshMinWorld, it.meshMinWorld.copy(x = it.meshMaxWorld.x),
                            it.meshMinWorld, it.meshMinWorld.copy(y = it.meshMaxWorld.y),
                            it.meshMinWorld, it.meshMinWorld.copy(z = it.meshMaxWorld.z),

                            it.meshMaxWorld, it.meshMaxWorld.copy(x = it.meshMinWorld.x),
                            it.meshMaxWorld, it.meshMaxWorld.copy(y = it.meshMinWorld.y),
                            it.meshMaxWorld, it.meshMaxWorld.copy(z = it.meshMinWorld.z),
                        )
                    }
                )
            }
        }
    }
}

fun Vector3f.copy(x: Float = this.x, y: Float = this.y, z: Float = this.z) = Vector3f(x, y, z)

fun createTransformBuffer() = BufferUtils.createFloatBuffer(16).apply { Transform().get(this) }
