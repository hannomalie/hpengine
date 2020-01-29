package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.graphics.light.point.CubeShadowMapStrategy
import de.hanno.hpengine.engine.graphics.profiled
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.AOScatteringExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.AmbientCubeGridExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.CombinePassRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.DirectionalLightSecondPassExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.ForwardRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.PointLightSecondPassExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.PostProcessingExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.SkyBoxRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DirectPipeline
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.graphics.state.StateRef

class ExtensibleDeferredRenderer(val engineContext: EngineContext<OpenGl>): RenderSystem, EngineContext<OpenGl> by engineContext {
    val drawlinesExtension = DrawLinesExtension(engineContext, programManager)
    val combinePassExtension = CombinePassRenderExtension(engineContext)
    val postProcessingExtension = PostProcessingExtension(engineContext)

    val simpleColorProgramStatic = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl")
    val simpleColorProgramAnimated = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl", Defines(Define.getDefine("ANIMATED", true)))

    val textureRenderer = SimpleTextureRenderer(engineContext, deferredRenderingBuffer.colorReflectivenessTexture)

    val pipeline: StateRef<DirectPipeline> = engineContext.renderStateManager.renderState.registerState {
        object: DirectPipeline(engineContext) {
            override fun customBeforeDrawAnimated(renderState: RenderState, program: Program, renderCam: Camera) {
                customBeforeDraw()
            }
            override fun customBeforeDrawStatic(renderState: RenderState, program: Program, renderCam: Camera) {
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

    val extensions: List<RenderExtension<OpenGl>> = listOf(
        SkyBoxRenderExtension(engineContext),
        ForwardRenderExtension(engineContext),
        DirectionalLightSecondPassExtension(engineContext),
        PointLightSecondPassExtension(engineContext),
        AOScatteringExtension(engineContext),
        AmbientCubeGridExtension(engineContext)
    )

    override fun update(deltaSeconds: Float) {
        val currentWriteState = engineContext.renderStateManager.renderState.currentWriteState
        currentWriteState.customState[pipeline].prepare(currentWriteState, currentWriteState.camera)
    }
    override fun render(result: DrawResult, state: RenderState): Unit = profiled("DeferredRendering") {
        gpuContext.depthMask = true
        deferredRenderingBuffer.use(gpuContext, true)

        if(engineContext.config.debug.isDrawBoundingVolumes) {

            drawlinesExtension.renderFirstPass(engineContext, gpuContext, result.firstPassResult, state)
        } else if(engineContext.config.debug.isDrawPointLightShadowMaps) {

            val cubeMapArrayRenderTarget = (state.lightState.pointLightShadowMapStrategy as? CubeShadowMapStrategy)?.cubemapArrayRenderTarget
            textureRenderer.renderCubeMapDebug(deferredRenderingBuffer.gBuffer, cubeMapArrayRenderTarget, cubeMapIndex = 0)
        } else {
            profiled("FirstPass") {

                profiled("MainPipeline") {
                    state[pipeline].draw(state, simpleColorProgramStatic,
                            simpleColorProgramAnimated, result.firstPassResult, state.camera)
                }

                for (extension in extensions) {
                    profiled(extension.javaClass.simpleName) {
                        extension.renderFirstPass(backend, gpuContext, result.firstPassResult, state)
                    }
                }
            }
            profiled("SecondPass") {
                profiled("HalfResolution") {
                    deferredRenderingBuffer.halfScreenBuffer.use(gpuContext, true)
                    for (extension in extensions) {
                        extension.renderSecondPassHalfScreen(state, result.secondPassResult)
                    }
                }
                deferredRenderingBuffer.lightAccumulationBuffer.use(gpuContext, true)
                for (extension in extensions) {
                    profiled(extension.javaClass.simpleName) {
                        extension.renderSecondPassFullScreen(state, result.secondPassResult)
                    }
                }
            }
            deferredRenderingBuffer.lightAccumulationBuffer.unUse()
            combinePassExtension.renderCombinePass(state)
        }

        runCatching {
            if(config.effects.isEnablePostprocessing) {
                // TODO This has to be implemented differently, so that
                // it is written to the final texture somehow
                profiled("PostProcessing") {
                    throw IllegalStateException("Render me to final map")
                    postProcessingExtension.renderSecondPassFullScreen(state, result.secondPassResult)
                }
            } else {
//                textureRenderer.drawToQuad(deferredRenderingBuffer.finalBuffer, deferredRenderingBuffer.lightAccumulationMapOneId)
            }
        }.onFailure {
            println("Not able to render texture")
        }

    }
}
