package de.hanno.hpengine.graphics.renderer

import com.artemis.World
import de.hanno.hpengine.backend.Backend
import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.renderer.constants.CullMode
import de.hanno.hpengine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.extensions.CombinePassRenderExtension
import de.hanno.hpengine.graphics.renderer.extensions.PostProcessingExtension
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.graphics.state.StateRef
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.graphics.*
import de.hanno.hpengine.graphics.renderer.pipelines.*
import net.miginfocom.swing.MigLayout
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JPanel

class ExtensibleDeferredRenderer(
    val window: Window<OpenGl>,
    val backend: Backend<OpenGl>,
    val config: Config,
    val deferredRenderingBuffer: DeferredRenderingBuffer,
    val renderStateManager: RenderStateManager,
    val deferredRenderExtensionConfig: DeferredRenderExtensionConfig,
    extensions: List<DeferredRenderExtension<OpenGl>>
) : RenderSystem, Backend<OpenGl> {
    override lateinit var artemisWorld: World
    private val allExtensions: List<DeferredRenderExtension<OpenGl>> = extensions.distinct()
    private val extensions: List<DeferredRenderExtension<OpenGl>>
        get() = deferredRenderExtensionConfig.run { allExtensions.filter { it.enabled } }

    override val sharedRenderTarget = deferredRenderingBuffer.gBuffer

    val combinePassExtension = CombinePassRenderExtension(config, backend.programManager, textureManager, backend.gpuContext, deferredRenderingBuffer)
    val postProcessingExtension = PostProcessingExtension(config, backend.programManager, textureManager, backend.gpuContext, deferredRenderingBuffer)

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
        Defines(Define("ANIMATED", true)),
        AnimatedFirstPassUniforms(gpuContext)
    )

    private val useIndirectRendering
        get() = config.performance.isIndirectRendering && gpuContext.isSupported(BindlessTextures)

    val indirectPipeline: StateRef<GPUCulledPipeline> = renderStateManager.renderState.registerState {
        object : GPUCulledPipeline(config, gpuContext, programManager, textureManager, deferredRenderingBuffer) {
            override fun beforeDrawAnimated(
                renderState: RenderState,
                program: Program<AnimatedFirstPassUniforms>,
                renderCam: Camera
            ) {
                super.beforeDrawAnimated(renderState, program, renderCam)
                customBeforeDraw()
            }

            override fun beforeDrawStatic(
                renderState: RenderState,
                program: Program<StaticFirstPassUniforms>,
                renderCam: Camera
            ) {
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
    private val staticDirectPipeline = renderStateManager.renderState.registerState {
        object: DirectFirstPassPipeline(config, gpuContext, simpleColorProgramStatic) {
            override fun RenderState.extractRenderBatches() = if(useIndirectRendering) {
                renderBatchesStatic.filterNot { it.canBeRenderedInIndirectBatch }
            } else renderBatchesStatic
        }
    }
    private val animatedDirectPipeline = renderStateManager.renderState.registerState {
        object: DirectFirstPassPipeline(config, gpuContext, simpleColorProgramAnimated) {
            override fun RenderState.extractRenderBatches() = if(useIndirectRendering) {
                renderBatchesAnimated.filterNot { it.canBeRenderedInIndirectBatch }
            } else renderBatchesAnimated

            override fun RenderState.selectVertexIndexBuffer() = vertexIndexBufferAnimated
        }
    }

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

        gpuContext.cullFace = true
        gpuContext.depthMask = true
        gpuContext.depthTest = true
        gpuContext.depthFunc = GlDepthFunc.LESS
        gpuContext.blend = false
        deferredRenderingBuffer.use(gpuContext, true)

        profiled("FirstPass") {

            profiled("MainPipeline") {
                renderState[staticDirectPipeline].draw(renderState)
                renderState[animatedDirectPipeline].draw(renderState)
            }

            if (useIndirectRendering) {
                renderState[indirectPipeline].draw(renderState, simpleColorProgramStatic, simpleColorProgramAnimated, renderState.latestDrawResult.firstPassResult)
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
                    deferredRenderingBuffer.lightAccumulationBuffer.use(gpuContext, false)
                    extension.renderSecondPassFullScreen(renderState, result.secondPassResult)
                }
            }
        }

        window.frontBuffer.use(gpuContext, false)
        combinePassExtension.renderCombinePass(renderState)
        deferredRenderingBuffer.use(gpuContext, false)

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

class DeferredRenderExtensionConfig(val renderExtensions: List<DeferredRenderExtension<*>>) {
    private val renderSystemsEnabled = renderExtensions.distinct().associateWith { true }.toMutableMap()
    var DeferredRenderExtension<*>.enabled: Boolean
        get() = renderSystemsEnabled[this]!!
        set(value) {
            renderSystemsEnabled[this] = value
        }
}