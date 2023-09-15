package de.hanno.hpengine.graphics.renderer.forward

import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.constants.DepthFunc
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.pipelines.AnimatedFirstPassUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.DirectFirstPassPipeline
import de.hanno.hpengine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.graphics.rendertarget.RenderTarget2D
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.state.StateRef
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.koin.core.annotation.Single
import org.koin.ksp.generated.module

@Single(binds = [RenderSystem::class])
class SimpleForwardRenderer(
    private val graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
    private val renderTarget: RenderTarget2D,
    private val programManager: ProgramManager,
    private val config: Config,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
): RenderSystem {

    val simpleColorProgramStatic = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(Define("COLOR_OUTPUT_0", true)),
        StaticFirstPassUniforms(graphicsApi)
    )

    val simpleColorProgramAnimated = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(Define("ANIMATED", true), Define("COLOR_OUTPUT_0", true)),
        AnimatedFirstPassUniforms(graphicsApi)
    )

    private val staticDirectPipeline: StateRef<DirectFirstPassPipeline> = renderStateContext.renderState.registerState {
        object: DirectFirstPassPipeline(graphicsApi, config, simpleColorProgramStatic, entitiesStateHolder, primaryCameraStateHolder) {
            override fun RenderState.extractRenderBatches() = this[entitiesStateHolder.entitiesState].renderBatchesStatic
        }
    }
    private val animatedDirectPipeline: StateRef<DirectFirstPassPipeline> = renderStateContext.renderState.registerState {
        object: DirectFirstPassPipeline(graphicsApi, config, simpleColorProgramAnimated,entitiesStateHolder, primaryCameraStateHolder) {
            override fun RenderState.extractRenderBatches() = this[entitiesStateHolder.entitiesState].renderBatchesAnimated

            override fun RenderState.selectVertexIndexBuffer() = this[entitiesStateHolder.entitiesState].vertexIndexBufferAnimated
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
        }
    }
}

val simpleForwardRendererModule = SimpleForwardRenderingModule().module