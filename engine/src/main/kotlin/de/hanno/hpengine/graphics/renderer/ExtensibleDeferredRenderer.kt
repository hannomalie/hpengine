package de.hanno.hpengine.graphics.renderer

import com.artemis.World
import de.hanno.hpengine.backend.Backend

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.renderer.constants.DepthFunc
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.extensions.CombinePassRenderExtension
import de.hanno.hpengine.graphics.renderer.extensions.PostProcessingExtension
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.graphics.state.StateRef
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.graphics.*
import de.hanno.hpengine.graphics.renderer.pipelines.*

context(GpuContext)
class ExtensibleDeferredRenderer(
    val window: Window,
    val backend: Backend,
    val config: Config,
    val deferredRenderingBuffer: DeferredRenderingBuffer,
    val renderStateManager: RenderStateManager,
    val deferredRenderExtensionConfig: DeferredRenderExtensionConfig,
    extensions: List<DeferredRenderExtension>,
) : RenderSystem {
    override lateinit var artemisWorld: World
    private val allExtensions: List<DeferredRenderExtension> = extensions.distinct()
    private val extensions: List<DeferredRenderExtension>
        get() = deferredRenderExtensionConfig.run { allExtensions.filter { it.enabled } }.sortedBy { it.renderPriority }

    private val programManager: ProgramManager = backend.programManager
    private val textureManager = backend.textureManager

    override val sharedRenderTarget = deferredRenderingBuffer.gBuffer

    val combinePassExtension = CombinePassRenderExtension(config, backend.programManager, textureManager, backend.gpuContext, deferredRenderingBuffer)
    val postProcessingExtension = PostProcessingExtension(config, backend.programManager, textureManager, backend.gpuContext, deferredRenderingBuffer)

    val simpleColorProgramStatic = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        StaticFirstPassUniforms()
    )

    val simpleColorProgramAnimated = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(Define("ANIMATED", true)),
        AnimatedFirstPassUniforms()
    )

    private val useIndirectRendering
        get() = config.performance.isIndirectRendering && isSupported(BindlessTextures)

    val indirectPipeline: StateRef<GPUCulledPipeline> = renderStateManager.renderState.registerState {
        GPUCulledPipeline(config, programManager, textureManager, deferredRenderingBuffer, true)
    }
    private val staticDirectPipeline: StateRef<DirectFirstPassPipeline> = renderStateManager.renderState.registerState {
        object: DirectFirstPassPipeline(config, simpleColorProgramStatic) {
            override fun RenderState.extractRenderBatches() = if(useIndirectRendering) {
                renderBatchesStatic.filterNot { it.canBeRenderedInIndirectBatch }
            } else renderBatchesStatic.filterNot { it.shouldBeSkipped(camera) }
        }
    }
    private val animatedDirectPipeline: StateRef<DirectFirstPassPipeline> = renderStateManager.renderState.registerState {
        object: DirectFirstPassPipeline(config, simpleColorProgramAnimated) {
            override fun RenderState.extractRenderBatches() = if(useIndirectRendering) {
                renderBatchesAnimated.filterNot { it.canBeRenderedInIndirectBatch }
            } else renderBatchesAnimated.filterNot { it.shouldBeSkipped(camera) }

            override fun RenderState.selectVertexIndexBuffer() = vertexIndexBufferAnimated
        }
    }

    override fun update(deltaSeconds: Float) {
        val currentWriteState = renderStateManager.renderState.currentWriteState

        preparePipelines(currentWriteState)

        extensions.forEach { it.update(deltaSeconds) }
    }

    private fun preparePipelines(currentWriteState: RenderState) {
        currentWriteState[staticDirectPipeline].prepare(currentWriteState)
        currentWriteState[animatedDirectPipeline].prepare(currentWriteState)
        if (useIndirectRendering) {
            currentWriteState.customState[indirectPipeline].prepare(currentWriteState)
        }
    }

    override fun extract(renderState: RenderState, world: World) {
        extensions.forEach { it.extract(renderState, world) }
    }

    override fun render(result: DrawResult, renderState: RenderState): Unit = profiled("DeferredRendering") {
        actualRender(renderState, result)
    }

    private fun actualRender(renderState: RenderState, result: DrawResult) {

        for (extension in extensions) {
            profiled(extension.javaClass.simpleName) {
                extension.renderZeroPass(renderState)
            }
        }

        if (useIndirectRendering) {
            renderState[indirectPipeline].copyDepthTexture()
        }

        cullFace = true
        depthMask = true
        depthTest = true
        depthFunc = DepthFunc.LESS
        blend = false
        deferredRenderingBuffer.use(true)

        profiled("FirstPass") {

            profiled("MainPipeline") {
                renderState[staticDirectPipeline].draw(renderState)
                renderState[animatedDirectPipeline].draw(renderState)
            }

            if (useIndirectRendering) {
                renderState[indirectPipeline].draw(
                    renderState,
                    simpleColorProgramStatic,
                    simpleColorProgramAnimated
                )
            }
            for (extension in extensions) {
                profiled(extension.javaClass.simpleName) {
                    extension.renderFirstPass(backend, result.firstPassResult, renderState)
                }
            }
        }
        profiled("SecondPass") {
            profiled("HalfResolution") {
                profiled("Use rendertarget") {
                    deferredRenderingBuffer.halfScreenBuffer.use(true)
                }
                for (extension in extensions) {
                    extension.renderSecondPassHalfScreen(renderState, result.secondPassResult)
                }
            }

            profiled("FullResolution") {
                profiled("Use rendertarget") {
                    deferredRenderingBuffer.lightAccumulationBuffer.use(true)
                }
                for (extension in extensions) {
                    profiled(extension.javaClass.simpleName) {
                        extension.renderSecondPassFullScreen(renderState, result.secondPassResult)
                    }
                }
            }
        }

        window.frontBuffer.use(false)
        combinePassExtension.renderCombinePass(renderState)
        deferredRenderingBuffer.use(false)

        runCatching {
            if (config.effects.isEnablePostprocessing) {
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
    }

    override fun renderEditor(result: DrawResult, renderState: RenderState) {
        extensions.forEach { it.renderEditor(renderState, result) }
    }

}

class DeferredRenderExtensionConfig(val renderExtensions: List<DeferredRenderExtension>) {
    private val renderSystemsEnabled = renderExtensions.distinct().associateWith { true }.toMutableMap()
    var DeferredRenderExtension.enabled: Boolean
        get() = renderSystemsEnabled[this]!!
        set(value) {
            renderSystemsEnabled[this] = value
        }
}
