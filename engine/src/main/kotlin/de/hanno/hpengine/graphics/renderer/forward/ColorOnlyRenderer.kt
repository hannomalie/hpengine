package de.hanno.hpengine.graphics.renderer.forward

import de.hanno.hpengine.Transform
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.cycle.CycleSystem
import de.hanno.hpengine.graphics.*
import de.hanno.hpengine.graphics.constants.DepthFunc
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.renderer.pipelines.*
import de.hanno.hpengine.graphics.rendertarget.RenderTarget2D
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.state.StateRef
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.skybox.SkyBoxStateHolder
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
        object: DirectPipeline(graphicsApi, config, simpleColorProgramStatic, entitiesStateHolder, entityBuffer, primaryCameraStateHolder, defaultBatchesSystem, materialSystem) {
            override fun RenderState.extractRenderBatches(camera: Camera) = this[defaultBatchesSystem.renderBatchesStatic].filter {
                !it.hasOwnProgram && it.isVisibleForCamera
            }
        }
    }
    private val animatedDirectPipeline: StateRef<DirectPipeline> = renderStateContext.renderState.registerState {
        object: DirectPipeline(graphicsApi, config, simpleColorProgramAnimated, entitiesStateHolder, entityBuffer, primaryCameraStateHolder, defaultBatchesSystem, materialSystem) {
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

        currentWriteState[staticDirectPipeline].preparedBatches.forEach {
            textureManager.setTexturesUsedInCycle(it.material.maps.values, cycleSystem.cycle)
        }
        currentWriteState[animatedDirectPipeline].preparedBatches.forEach {
            textureManager.setTexturesUsedInCycle(it.material.maps.values, cycleSystem.cycle)
        }
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
                simpleColorProgramSkyBox.setTextureUniforms(graphicsApi, batch.material.maps, null)
            }, block = {
                renderState[entitiesStateHolder.entitiesState].geometryBufferStatic.draw(
                    batch.drawElementsIndirectCommand,
                    primitiveType = PrimitiveType.Triangles,
                    mode = if(config.debug.isDrawLines) RenderingMode.Lines else RenderingMode.Fill,
                    bindIndexBuffer = true,
                )
            })
        }
    }
}

fun createTransformBuffer() = BufferUtils.createFloatBuffer(16).apply { Transform().get(this) }

val simpleForwardRendererModule = SimpleForwardRenderingModule().module