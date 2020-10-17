package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.light.point.CubeShadowMapStrategy
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.AOScatteringExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.BvHPointLightSecondPassExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.CombinePassRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.DirectionalLightSecondPassExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.ForwardRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.PostProcessingExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.AnimatedFirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DirectPipeline
import de.hanno.hpengine.engine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.graphics.state.StateRef
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import kotlinx.coroutines.CoroutineScope
import org.lwjgl.opengl.GL11

class ExtensibleDeferredRenderer(val engineContext: EngineContext): RenderSystem, Backend<OpenGl> {
    val window: Window<OpenGl> = engineContext.window
    val backend: Backend<OpenGl> = engineContext.backend
    val config: Config = engineContext.config
    val deferredRenderingBuffer: DeferredRenderingBuffer = engineContext.deferredRenderingBuffer
    val renderSystems: MutableList<RenderSystem> = engineContext.renderSystems
    val renderStateManager: RenderStateManager = engineContext.renderStateManager
    val materialManager: MaterialManager = engineContext.materialManager

    val drawlinesExtension = DrawLinesExtension(engineContext)
    val combinePassExtension = CombinePassRenderExtension(engineContext)
    val postProcessingExtension = PostProcessingExtension(engineContext)

    val simpleColorProgramStatic = programManager.getProgram(
            config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
            config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
            null,
            Defines(),
            StaticFirstPassUniforms(gpuContext)
    )
    val simpleColorProgramAnimated = programManager.getProgram(
            config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
            config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
            null,
            Defines(Define.getDefine("ANIMATED", true)),
            AnimatedFirstPassUniforms(gpuContext)
    )

    val textureRenderer = SimpleTextureRenderer(engineContext, deferredRenderingBuffer.colorReflectivenessTexture)

    val pipeline: StateRef<DirectPipeline> = engineContext.renderStateManager.renderState.registerState {
        object: DirectPipeline(engineContext) {
            override fun beforeDrawAnimated(renderState: RenderState, program: Program<AnimatedFirstPassUniforms>, renderCam: Camera) {
                super.beforeDrawAnimated(renderState, program, renderCam)
                customBeforeDraw()
            }
            override fun beforeDrawStatic(renderState: RenderState, program: Program<StaticFirstPassUniforms>, renderCam: Camera) {
                super.beforeDrawStatic(renderState, program, renderCam)
                customBeforeDraw()
            }
            private fun customBeforeDraw() {
                deferredRenderingBuffer.use(gpuContext, false)
                gpuContext.cullFace = true
                gpuContext.depthMask = true
                gpuContext.depthTest = true
                gpuContext.depthFunc = GlDepthFunc.LESS
                gpuContext.blend = false
            }
        }
    }

    val directionalLightSecondPassExtension = DirectionalLightSecondPassExtension(engineContext)
    val extensions: MutableList<RenderExtension<OpenGl>> = mutableListOf(
        ForwardRenderExtension(engineContext),
        directionalLightSecondPassExtension,
//        PointLightSecondPassExtension(engineContext),
        AOScatteringExtension(engineContext),
//        AmbientCubeGridExtension(engineContext),
//        VoxelConeTracingExtension(engineContext, shadowMapExtension, this),
        BvHPointLightSecondPassExtension(engineContext)
    )
    override val eventBus
        get() = backend.eventBus
    override val gpuContext: GpuContext<OpenGl>
        get() = backend.gpuContext
    override val programManager: ProgramManager<OpenGl>
        get() = backend.programManager
    override val textureManager: TextureManager
        get() = backend.textureManager
    override val input: Input
        get() = backend.input
    override val addResourceContext: AddResourceContext
        get() = backend.addResourceContext

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        val currentWriteState = this@ExtensibleDeferredRenderer.engineContext.renderStateManager.renderState.currentWriteState

        currentWriteState.customState[this@ExtensibleDeferredRenderer.pipeline].prepare(currentWriteState, currentWriteState.camera)

        this@ExtensibleDeferredRenderer.extensions.forEach { it.run { update(scene, deltaSeconds) } }
    }

    override fun extract(scene: Scene, renderState: RenderState) {
        extensions.forEach { it.extract(scene, renderState) }
    }

    override fun render(result: DrawResult, renderState: RenderState): Unit = profiled("DeferredRendering") {
        gpuContext.depthMask = true
        deferredRenderingBuffer.use(gpuContext, true)

        if(engineContext.config.debug.isDrawBoundingVolumes) {

            drawlinesExtension.renderFirstPass(engineContext.backend, gpuContext, result.firstPassResult, renderState)
        } else if(engineContext.config.debug.isDrawPointLightShadowMaps) {

            val cubeMapArrayRenderTarget = (renderState.lightState.pointLightShadowMapStrategy as? CubeShadowMapStrategy)?.cubemapArrayRenderTarget
            textureRenderer.renderCubeMapDebug(deferredRenderingBuffer.gBuffer, cubeMapArrayRenderTarget, cubeMapIndex = 0)
        } else {
            profiled("FirstPass") {

                profiled("MainPipeline") {
                    renderState[pipeline].draw(renderState, simpleColorProgramStatic, simpleColorProgramAnimated, result.firstPassResult)
                }

                for (extension in extensions) {
                    profiled(extension.javaClass.simpleName) {
                        extension.renderFirstPass(backend, gpuContext, result.firstPassResult, renderState)
                    }
                }
            }
            profiled("SecondPass") {
                profiled("HalfResolution") {
                    deferredRenderingBuffer.halfScreenBuffer.use(gpuContext, true)
                    for (extension in extensions) {
                        extension.renderSecondPassHalfScreen(renderState, result.secondPassResult)
                    }
                }
                deferredRenderingBuffer.lightAccumulationBuffer.use(gpuContext, true)
                for (extension in extensions) {
                    profiled(extension.javaClass.simpleName) {
                        extension.renderSecondPassFullScreen(renderState, result.secondPassResult)
                    }
                }
            }
            deferredRenderingBuffer.lightAccumulationBuffer.unUse()
            combinePassExtension.renderCombinePass(renderState)
        }

        runCatching {
            if(config.effects.isEnablePostprocessing) {
                // TODO This has to be implemented differently, so that
                // it is written to the final texture somehow
                profiled("PostProcessing") {
                    throw IllegalStateException("Render me to final map")
                    postProcessingExtension.renderSecondPassFullScreen(renderState, result.secondPassResult)
                }
            } else {
//                textureRenderer.drawToQuad(deferredRenderingBuffer.finalBuffer, deferredRenderingBuffer.lightAccumulationMapOneId)
            }
        }.onFailure {
            println("Not able to render texture")
        }

        // TODO: This should not be needed
        GL11.glFinish()
    }
}
